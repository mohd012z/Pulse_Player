"""Verify AI Pulse training outputs without executing checkpoint code.

This scanner deliberately recognizes safetensors only. Pickle-based PyTorch
checkpoints require a separately reviewed, isolated loading procedure.
"""
import argparse
import hashlib
import json
from pathlib import Path
from model_evidence import inspect_safetensors

def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def verify_checkpoints(output_dir, *, max_files=2000):
    root = Path(output_dir).resolve()
    if not root.is_dir():
        raise ValueError("Training output directory does not exist")
    candidates = sorted(root.rglob("*.safetensors"))
    if len(candidates) > max_files:
        raise ValueError("Too many checkpoint files")
    records = []
    for path in candidates:
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError("Checkpoint path escapes output directory")
        try:
            details = inspect_safetensors(path)
            records.append({"path": str(path.relative_to(root)),
                            "sha256": sha256(path), "inspection": details})
        except (ValueError, OSError, json.JSONDecodeError) as error:
            records.append({"path": str(path.relative_to(root)),
                            "error": type(error).__name__})
    valid = [item for item in records if "inspection" in item
             and item["inspection"]["tensor_count"] > 0]
    return {"status": "CHECKPOINT_STRUCTURE_VERIFIED_INFERENCE_NOT_PROVEN"
            if valid else "NO_VERIFIED_SAFETENSORS_CHECKPOINT",
            "valid_checkpoints": len(valid), "inspected_files": len(records),
            "files": records, "inference_verified": False,
            "training_improvement_verified": False}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    report = verify_checkpoints(args.output_dir)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"],
                      "valid_checkpoints": report["valid_checkpoints"]}))
    if not report["valid_checkpoints"]:
        raise SystemExit(2)

if __name__ == "__main__":
    main()
