"""AI Pulse P0-P4 release evidence gate.

Requires real model, training, inference, and device evidence before declaring
a neural song-generation release ready. No synthetic proof is accepted.
"""
import argparse
import hashlib
import json
from pathlib import Path

REQUIRED = (
    "source_license",
    "dataset_consent",
    "model_checkpoint",
    "training_metrics",
    "generated_audio",
    "android_test",
)


def _hash(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def evaluate(root, evidence):
    root = Path(root).resolve()
    if not isinstance(evidence, dict):
        raise ValueError("Evidence must be an object")
    checks = {}
    for key in REQUIRED:
        item = evidence.get(key)
        if not isinstance(item, dict):
            checks[key] = {"passed": False, "reason": "missing evidence"}
            continue
        filename, expected = item.get("path"), item.get("sha256")
        if not isinstance(filename, str) or not filename or not isinstance(expected, str) or len(expected) != 64:
            checks[key] = {"passed": False, "reason": "missing path or sha256"}
            continue
        path = (root / filename).resolve()
        if not path.is_relative_to(root) or not path.is_file() or path.is_symlink():
            checks[key] = {"passed": False, "reason": "invalid evidence path"}
            continue
        actual = _hash(path)
        passed = actual == expected.lower()
        checks[key] = {"passed": passed, "reason": "verified" if passed else "sha256 mismatch"}
    passed = all(item["passed"] for item in checks.values())
    return {"status": "EVIDENCE_FILES_VERIFIED_MANUAL_REVIEW_REQUIRED" if passed else "BLOCKED_MISSING_EVIDENCE",
            "ready_for_release": False,
            "checks": checks,
            "manual_review_required": ["license scope", "dataset rights", "audio listening evaluation",
                                       "real inference provenance", "Android hardware test results"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = evaluate(args.root, json.loads(args.manifest.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    if report["status"] == "BLOCKED_MISSING_EVIDENCE":
        raise SystemExit(2)


if __name__ == "__main__":
    main()
