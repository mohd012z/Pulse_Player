"""AI Pulse P0-P4 reproducible local production pipeline.

P0: ONNX acoustic + vocoder with explicit tensor contract.
P1: validated phoneme/duration/F0 phrase contract.
P2: authorized dataset validation through voice_dataset.py.
P3: WAV stem mixing with clipping prevention.
P4: performance metrics and machine-readable evidence.
Requires user-provided licensed model weights. Does not impersonate voices.
"""
import argparse
import hashlib
import json
import math
import struct
import time
import wave
from pathlib import Path


def validate_phrase(data):
    phones = data["phonemes"]
    durations = data["durations"]
    f0 = data["f0"]
    if not phones or len(phones) != len(durations):
        raise ValueError("Phoneme and duration counts must match")
    if any(type(v) is not int or v < 0 for v in phones):
        raise ValueError("Invalid phoneme IDs")
    if any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in durations):
        raise ValueError("Durations must be finite positive values")
    if not f0 or any(type(v) not in (int, float) or not math.isfinite(v) or v < 0 or v > 3000 for v in f0):
        raise ValueError("F0 values must be finite and between 0 and 3000 Hz")
    return data


def _sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _session(path):
    import onnxruntime as ort
    return ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])


def _contract(session, expected):
    actual = {i.name: i for i in session.get_inputs()}
    if set(actual) != set(expected):
        raise ValueError(f"ONNX inputs {list(actual)} differ from contract {list(expected)}")
    for name, dtype in expected.items():
        if actual[name].type != dtype:
            raise ValueError(f"ONNX input {name}: expected {dtype}, got {actual[name].type}")


def render(config, phrase, output):
    """Render only explicit ONNX signatures; reject unknown architectures."""
    import numpy as np
    cfg = json.loads(Path(config).read_text(encoding="utf-8"))
    phrase = validate_phrase(json.loads(Path(phrase).read_text(encoding="utf-8")))
    acoustic_path = Path(cfg["acoustic"]).resolve()
    vocoder_path = Path(cfg["vocoder"]).resolve()
    rate = int(cfg["sample_rate"])
    hop = int(cfg["hop_size"])
    mel_bins = int(cfg["mel_bins"])
    if not (16000 <= rate <= 96000 and 64 <= hop <= 2048 and 16 <= mel_bins <= 256):
        raise ValueError("Unsupported audio configuration")
    acoustic = _session(acoustic_path)
    vocoder = _session(vocoder_path)
    _contract(acoustic, {"tokens": "tensor(int64)", "durations": "tensor(float)", "f0": "tensor(float)", "speedup": "tensor(int64)"})
    _contract(vocoder, {"mel": "tensor(float)", "f0": "tensor(float)"})
    phones = np.asarray([phrase["phonemes"]], dtype=np.int64)
    durations = np.asarray([phrase["durations"]], dtype=np.float32)
    f0 = np.asarray([phrase["f0"]], dtype=np.float32)
    t0 = time.perf_counter()
    mel = acoustic.run(None, {
        "tokens": phones, "durations": durations, "f0": f0,
        "speedup": np.asarray([int(cfg.get("speedup", 1))], dtype=np.int64),
    })[0]
    if mel.ndim != 3 or mel.shape[0] != 1 or mel.shape[2] != mel_bins:
        raise ValueError(f"Acoustic output shape {mel.shape} incompatible with mel_bins={mel_bins}")
    frames = mel.shape[1]
    if frames <= 0 or frames > 50000 or not np.isfinite(mel).all():
        raise ValueError("Invalid mel output")
    if f0.shape[1] != frames:
        raise ValueError(f"F0 frames {f0.shape[1]} != acoustic mel frames {frames}")
    audio = vocoder.run(None, {"mel": mel.astype(np.float32), "f0": f0})[0]
    audio = np.asarray(audio, dtype=np.float32).reshape(-1)
    if len(audio) < 256 or not np.isfinite(audio).all():
        raise ValueError("Invalid vocoder waveform")
    peak = float(np.max(np.abs(audio)))
    rms = float(np.sqrt(np.mean(audio * audio)))
    if peak < 1e-5 or rms < 1e-6 or peak > 5:
        raise ValueError("Silent or invalid vocoder waveform")
    audio = np.clip(audio, -1, 1)
    _write_wav(output, rate, audio)
    elapsed = time.perf_counter() - t0
    evidence = {
        "status": "INFERENCE_EXECUTED_NOT_PERCEPTUALLY_VERIFIED",
        "acoustic_sha256": _sha(acoustic_path),
        "vocoder_sha256": _sha(vocoder_path),
        "wav_sha256": _sha(output),
        "sample_rate": rate, "hop_size": hop, "mel_bins": mel_bins,
        "frames": frames, "samples": len(audio),
        "peak": peak, "rms": rms,
        "render_seconds": round(elapsed, 4),
        "real_time_factor": round(elapsed / (len(audio) / rate), 4),
    }
    Path(str(output) + ".evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
    return evidence


def _write_wav(path, rate, samples):
    import numpy as np
    samples = np.asarray(samples, dtype=np.float64)
    if not len(samples) or not np.isfinite(samples).all():
        raise ValueError("Cannot write empty or nonfinite audio")
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        wav.writeframes(pcm.tobytes())


def _read_wav(path):
    import numpy as np
    with wave.open(str(path), "rb") as wav:
        if wav.getnchannels() != 1 or wav.getsampwidth() != 2 or wav.getcomptype() != "NONE":
            raise ValueError("Only mono PCM16 WAV stems supported")
        rate = wav.getframerate()
        audio = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float64) / 32768.0
    return rate, audio


def mix(stems, output, gains=None):
    import numpy as np
    if not stems:
        raise ValueError("No stems supplied")
    gains = gains or [1.0] * len(stems)
    if len(gains) != len(stems) or any(not math.isfinite(g) or g < 0 or g > 4 for g in gains):
        raise ValueError("Invalid stem gains")
    loaded = [_read_wav(path) for path in stems]
    rate = loaded[0][0]
    if any(r != rate for r, _ in loaded):
        raise ValueError("Sample rates must match; resample before mixing")
    size = max(len(a) for _, a in loaded)
    result = np.zeros(size, dtype=np.float64)
    for (_, samples), gain in zip(loaded, gains):
        result[:len(samples)] += samples * gain
    peak = float(np.max(np.abs(result))) if size else 0
    if peak > 0.98:
        result *= 0.98 / peak
    _write_wav(output, rate, result)
    return {"sample_rate": rate, "samples": size, "input_peak": peak, "wav_sha256": _sha(output)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("validate-phrase")
    p.add_argument("phrase", type=Path)
    p = sub.add_parser("render")
    p.add_argument("--config", type=Path, required=True)
    p.add_argument("--phrase", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p = sub.add_parser("mix")
    p.add_argument("--stems", nargs="+", type=Path, required=True)
    p.add_argument("--gains", nargs="+", type=float)
    p.add_argument("--output", type=Path, required=True)
    p = sub.add_parser("validate-dataset")
    p.add_argument("dataset", type=Path)
    args = parser.parse_args()
    if args.command == "validate-phrase":
        result = {"status": "VALID", "phonemes": len(validate_phrase(json.loads(args.phrase.read_text()))["phonemes"])}
    elif args.command == "render":
        result = render(args.config, args.phrase, args.output)
    elif args.command == "mix":
        result = mix(args.stems, args.output, args.gains)
    else:
        from voice_dataset import inspect_dataset
        result = inspect_dataset(args.dataset)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
