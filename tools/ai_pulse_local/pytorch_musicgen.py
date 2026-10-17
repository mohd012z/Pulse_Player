"""Licensed MusicGen PyTorch inference backend for AI Pulse.

Usage:
 python pytorch_musicgen.py --prompt "original jazz instrumental" --output song.wav
Requires audiocraft, torch, and noncommercial MusicGen weight permission.
No automatic downloading or proprietary service access is performed here.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import time
import wave


def render(prompt, output, *, model_name="facebook/musicgen-small",
           seconds=10, device="auto", license_ack=False):
    if not license_ack:
        raise ValueError("Explicit acceptance of MusicGen CC BY-NC 4.0 weights required")
    if model_name not in ("facebook/musicgen-small", "facebook/musicgen-medium",
                          "facebook/musicgen-large", "facebook/musicgen-melody"):
        raise ValueError("Model must be from the approved MusicGen allowlist")
    if not isinstance(prompt, str) or not 1 <= len(prompt.strip()) <= 1000:
        raise ValueError("Invalid prompt")
    if not isinstance(seconds, (int, float)) or not math.isfinite(seconds) or not 1 <= seconds <= 30:
        raise ValueError("MusicGen generation must be between 1 and 30 seconds")
    output = Path(output)
    if output.exists():
        raise FileExistsError(output)
    if output.suffix.lower() != ".wav":
        raise ValueError("Output must be a WAV file")
    import torch
    from audiocraft.models import MusicGen
    if device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    if device not in ("cpu", "cuda") or (device == "cuda" and not torch.cuda.is_available()):
        raise ValueError("Requested device unavailable")
    started = time.perf_counter()
    model = MusicGen.get_pretrained(model_name, device=device)
    model.set_generation_params(duration=float(seconds))
    with torch.inference_mode():
        waveform = model.generate([prompt.strip()]).detach().cpu()
    if waveform.ndim != 3 or waveform.shape[0] != 1:
        raise ValueError("Unexpected MusicGen audio shape")
    data = waveform[0]
    if data.shape[0] not in (1, 2) or data.shape[1] < 100:
        raise ValueError("Unexpected MusicGen channel count or duration")
    if not torch.isfinite(data).all():
        raise ValueError("Nonfinite generated samples")
    peak = float(data.abs().max().item())
    if peak < 1e-5:
        raise ValueError("Generated audio is silent")
    data = (data.clamp(-1, 1).transpose(0, 1).contiguous() * 32767).to(torch.int16)
    sample_rate = int(model.sample_rate)
    output.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output), "wb") as wav:
        wav.setnchannels(data.shape[1])
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(data.numpy().astype("<i2", copy=False).tobytes())
    sha = hashlib.sha256(output.read_bytes()).hexdigest()
    result = {"status": "PYTORCH_AUDIO_RENDERED_NOT_HUMAN_REVIEWED",
              "backend": "musicgen", "model": model_name,
              "model_weight_license": "CC-BY-NC-4.0", "commercial_use": False,
              "device": device, "sample_rate": sample_rate,
              "channels": int(data.shape[1]), "sha256": sha,
              "elapsed_seconds": round(time.perf_counter() - started, 3)}
    Path(str(output) + ".evidence.json").write_text(json.dumps(result, indent=2) + "\n")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--prompt", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", default="facebook/musicgen-small")
    parser.add_argument("--seconds", type=float, default=10)
    parser.add_argument("--device", default="auto", choices=["auto", "cpu", "cuda"])
    parser.add_argument("--acknowledge-noncommercial-license", action="store_true")
    args = parser.parse_args()
    result = render(args.prompt, args.output, model_name=args.model,
                    seconds=args.seconds, device=args.device,
                    license_ack=args.acknowledge_noncommercial_license)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
