"""Offline/online-ready DiffSinger voicebank structural preflight.
No external downloads, model weights, or unauthorized voice assets included.
"""
import argparse
import hashlib
import json
from pathlib import Path

MAX_MODEL = 512 * 1024 * 1024


def _field(text, key):
    # Deliberately accepts only simple unquoted scalar YAML keys.
    for line in text.splitlines():
        raw = line.split("#", 1)[0]
        if raw.startswith((" ", "\t")) or ":" not in raw:
            continue
        k, v = raw.split(":", 1)
        if k.strip() == key:
            return v.strip().strip("'\"")
    return ""


def _safe_file(root, name, *, model=False):
    if not name or Path(name).name != name or name in (".", ".."):
        raise ValueError("Unsafe or missing filename")
    path = root / name
    if not path.is_file() or path.is_symlink() or path.resolve().parent != root.resolve():
        raise ValueError(f"Missing/unsafe file: {name}")
    if not 0 < path.stat().st_size <= (MAX_MODEL if model else 65536):
        raise ValueError(f"Invalid file size: {name}")
    return path


def inspect(folder):
    folder = Path(folder).resolve()
    config = _safe_file(folder, "dsconfig.yaml").read_text(encoding="utf-8")
    acoustic = _safe_file(folder, _field(config, "acoustic"), model=True)
    phonemes = _safe_file(folder, _field(config, "phonemes") or "phonemes.txt")
    tokens = [s.strip() for s in phonemes.read_text(encoding="utf-8").splitlines() if s.strip()]
    if not tokens or len(tokens) != len(set(tokens)):
        raise ValueError("Missing or duplicate phonemes")
    vocoder_name = _field(config, "vocoder")
    if not vocoder_name:
        raise ValueError("Vocoder not configured")
    report = {
        "status": "STRUCTURAL_ONLY_NOT_INFERENCE_PROVEN",
        "acoustic": acoustic.name,
        "acoustic_sha256": hashlib.sha256(acoustic.read_bytes()).hexdigest(),
        "phoneme_count": len(tokens),
        "vocoder_reference": vocoder_name,
        "requires_external_vocoder": not (folder / "dsvocoder" / "vocoder.onnx").is_file(),
        "optional_models": [x for x in ("dsdur", "dspitch", "dsvariance") if (folder / x).is_dir()],
    }
    voc = folder / "dsvocoder"
    if voc.is_dir():
        model = _safe_file(voc, "vocoder.onnx", model=True)
        config_path = _safe_file(voc, "vocoder.yaml")
        voc_config = config_path.read_text(encoding="utf-8")
        report["vocoder_sha256"] = hashlib.sha256(model.read_bytes()).hexdigest()
        for key in ("sample_rate", "hop_size", "num_mel_bins"):
            val = _field(voc_config, key)
            if not val.isdigit() or int(val) <= 0:
                raise ValueError(f"Invalid vocoder {key}")
            report[key] = int(val)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = inspect(args.folder)
    output = json.dumps(result, indent=2)
    if args.report:
        args.report.write_text(output + "\n", encoding="utf-8")
    print(output)
