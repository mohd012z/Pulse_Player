"""Run explicitly configured local music backends; no proprietary API scraping.

The backend executable is selected by the operator via a trusted absolute
path. Arguments are passed without a shell, and the produced WAV is verified.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import time
import wave

from music_provider_guardrails import build_plan


def inspect_wav(path):
    path = Path(path)
    if not path.is_file() or path.is_symlink():
        raise ValueError("No regular WAV output produced")
    if not 44 <= path.stat().st_size <= 1024 * 1024 * 1024:
        raise ValueError("WAV size outside safe bounds")
    with wave.open(str(path), "rb") as audio:
        if audio.getcomptype() != "NONE" or audio.getsampwidth() != 2:
            raise ValueError("Output must be uncompressed PCM16 WAV")
        if audio.getnchannels() not in (1, 2) or not 16000 <= audio.getframerate() <= 96000:
            raise ValueError("Unsupported output channel count or sample rate")
        sample_rate = audio.getframerate()
        channels = audio.getnchannels()
        seconds = audio.getnframes() / sample_rate
        if seconds < 0.1 or seconds > 360:
            raise ValueError("Output duration outside bounds")
        peak = 0
        frames = 0
        while True:
            block = audio.readframes(32768)
            if not block:
                break
            from array import array
            samples = array("h")
            samples.frombytes(block)
            if os.sys.byteorder != "little":
                samples.byteswap()
            peak = max(peak, max(abs(v) for v in samples))
            frames += len(samples)
        if peak < 4:
            raise ValueError("Output audio is silent")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"duration_seconds": round(seconds, 3), "sample_rate": sample_rate,
            "channels": channels, "peak_pcm16": peak,
            "sha256": digest.hexdigest()}


def run(request, backend_config, output, timeout=900):
    plan = build_plan(request)
    cfg = json.loads(Path(backend_config).read_text(encoding="utf-8"))
    if cfg.get("backend") != plan["backend"]:
        raise ValueError("Backend configuration mismatch")
    executable = Path(cfg["executable"])
    if not executable.is_absolute() or not executable.is_file() or executable.is_symlink():
        raise ValueError("Backend executable must be a trusted absolute regular file")
    if not cfg.get("operator_approved"):
        raise ValueError("Operator approval of installed backend required")
    if not 1 <= timeout <= 1800:
        raise ValueError("Timeout outside safe range")
    output = Path(output).resolve()
    if output.exists():
        raise FileExistsError(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    args = cfg.get("args")
    if not isinstance(args, list) or not args or len(args) > 32:
        raise ValueError("Explicit backend argument list required")
    replacements = {"{prompt}": plan["prompt"], "{output}": str(output),
                    "{duration}": str(plan["duration_seconds"])}
    command = [str(executable)]
    for arg in args:
        if not isinstance(arg, str) or len(arg) > 2048:
            raise ValueError("Invalid backend argument")
        for token, replacement in replacements.items():
            arg = arg.replace(token, replacement)
        command.append(arg)
    start = time.monotonic()
    try:
        completed = subprocess.run(command, shell=False, check=False, timeout=timeout,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if completed.returncode:
            raise RuntimeError(f"Backend failed with exit code {completed.returncode}")
        evidence = inspect_wav(output)
    except BaseException:
        output.unlink(missing_ok=True)
        raise
    evidence.update({"backend": plan["backend"], "status": "AUDIO_RENDERED_NOT_HUMAN_REVIEWED",
                     "elapsed_seconds": round(time.monotonic() - start, 3),
                     "provider_source": plan["source"]})
    Path(str(output) + ".evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
    return evidence


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--request", type=Path, required=True)
    parser.add_argument("--backend-config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()
    result = run(json.loads(args.request.read_text(encoding="utf-8")),
                 args.backend_config, args.output, args.timeout)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
