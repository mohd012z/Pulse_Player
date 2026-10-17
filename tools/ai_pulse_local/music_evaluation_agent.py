"""AI Pulse P3 music evaluation agent.

Evaluates real PCM WAV artifacts for technical defects. This is not a
perceptual quality, lyric accuracy, or model-strength benchmark.
"""
import argparse
import hashlib
import json
import math
import struct
import wave
from pathlib import Path


def evaluate_wav(path, *, max_seconds=600):
    path = Path(path)
    if not path.is_file() or path.is_symlink():
        raise ValueError("Expected a regular WAV file")
    with wave.open(str(path), "rb") as wav:
        channels = wav.getnchannels()
        width = wav.getsampwidth()
        rate = wav.getframerate()
        frames = wav.getnframes()
        if channels not in (1, 2) or width != 2 or not 8000 <= rate <= 192000:
            raise ValueError("Only mono/stereo PCM16 WAV supported")
        if frames == 0 or frames > rate * max_seconds:
            raise ValueError("Invalid WAV duration")
        peak = 0
        sum_sq = 0
        clipped = 0
        count = 0
        while True:
            block = wav.readframes(32768)
            if not block:
                break
            if len(block) % 2:
                raise ValueError("Truncated PCM sample")
            samples = struct.unpack("<" + "h" * (len(block) // 2), block)
            for sample in samples:
                absolute = abs(sample)
                peak = max(peak, absolute)
                clipped += absolute >= 32767
                sum_sq += sample * sample
            count += len(samples)
    if count != frames * channels:
        raise ValueError("Truncated PCM frames")
    rms = math.sqrt(sum_sq / count) / 32768
    peak_ratio = peak / 32768
    clipping_fraction = clipped / count
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    issues = []
    if rms < 0.0001:
        issues.append("SILENT_OR_NEAR_SILENT")
    if clipping_fraction > 0.001:
        issues.append("EXCESSIVE_CLIPPING")
    return {
        "status": "TECHNICAL_WAV_CHECK_PASSED" if not issues else "TECHNICAL_WAV_CHECK_FAILED",
        "sha256": digest,
        "sample_rate": rate,
        "channels": channels,
        "duration_seconds": round(frames / rate, 3),
        "peak_dbfs": round(20 * math.log10(max(peak_ratio, 1e-12)), 2),
        "rms_dbfs": round(20 * math.log10(max(rms, 1e-12)), 2),
        "clipping_fraction": round(clipping_fraction, 8),
        "issues": issues,
        "perceptual_quality_verified": False,
        "lyric_accuracy_verified": False,
        "model_equivalence_verified": False,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("wav", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = evaluate_wav(args.wav)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    if report["issues"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
