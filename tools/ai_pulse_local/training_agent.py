"""AI Pulse supervised training agent.

Runs approved local preflight and training commands, tracks evidence, and
recommends the next step. Never autonomously downloads weights, trains on
unlicensed audio, or modifies model weights without explicit --execute.
"""
import argparse
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

from ace_training_gate import prepare


def digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def make_plan(spec):
    training = prepare(spec)
    return {
        "agent": "ai_pulse_training_agent_v1",
        "status": "AWAITING_OPERATOR_APPROVAL",
        "training": training,
        "steps": [
            {"phase": "P0", "task": "verify model files and licenses", "state": "needs_evidence"},
            {"phase": "P1", "task": "verify authorized preprocessed dataset", "state": "needs_evidence"},
            {"phase": "P2", "task": "run LoRA training and capture logs", "state": "pending"},
            {"phase": "P3", "task": "generate audio and evaluate WAV quality", "state": "pending"},
            {"phase": "P4", "task": "validate Android integration and release", "state": "pending"},
        ],
    }


def run(spec, *, execute=False):
    plan = make_plan(spec)
    out = Path(spec["output_dir"]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    plan_file = out / "ai_pulse_agent_plan.json"
    plan_file.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    if not execute:
        return plan
    if not spec.get("operator_approved", False):
        raise PermissionError("Training requires operator_approved=true")
    if not spec.get("verified_source_revision") or not spec.get("verified_dataset_manifest_sha256"):
        raise ValueError("Pinned source revision and dataset manifest SHA256 required")
    revision = spec["verified_source_revision"]
    dataset_hash = spec["verified_dataset_manifest_sha256"]
    if (not isinstance(revision, str) or len(revision) != 40
            or any(c not in "0123456789abcdef" for c in revision)
            or not isinstance(dataset_hash, str) or len(dataset_hash) != 64
            or any(c not in "0123456789abcdef" for c in dataset_hash)):
        raise ValueError("Invalid source revision or dataset digest")
    manifest = Path(spec["dataset_manifest"]).resolve()
    dataset = Path(spec["dataset_dir"]).resolve()
    if not manifest.is_file() or not manifest.is_relative_to(dataset):
        raise ValueError("Dataset manifest must exist inside the dataset directory")
    if digest(manifest) != dataset_hash:
        raise ValueError("Dataset manifest hash mismatch")
    started = time.monotonic()
    log = out / "ai_pulse_training.log"
    command = plan["training"]["command"]
    with log.open("w", encoding="utf-8") as stream:
        completed = subprocess.run(command, cwd=plan["training"]["cwd"],
                                   stdout=stream, stderr=subprocess.STDOUT,
                                   check=False, timeout=spec.get("timeout_seconds", 86400))
    evidence = {
        "status": "TRAINING_EXITED_ZERO_CHECKPOINT_UNVERIFIED" if completed.returncode == 0 else "TRAINING_FAILED",
        "returncode": completed.returncode,
        "elapsed_seconds": round(time.monotonic() - started, 2),
        "training_log_sha256": digest(log),
        "source_revision": revision,
        "dataset_manifest_sha256": dataset_hash,
        "checkpoint_verified": False,
        "audio_generated": False,
        "android_verified": False,
    }
    (out / "ai_pulse_training_evidence.json").write_text(
        json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    return evidence


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("config", type=Path)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    spec = json.loads(args.config.read_text(encoding="utf-8"))
    report = run(spec, execute=args.execute)
    print(json.dumps(report, indent=2))
    if report.get("returncode", 0):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
