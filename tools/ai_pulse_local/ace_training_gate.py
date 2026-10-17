"""AI Pulse P0-P4 ACE-Step Side-Step training orchestrator.

Preflight and command generation only by default. Actual training is opt-in,
requires authorized data, and uses the official Side-Step train.py entrypoint.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys


def prepare(spec):
    root = Path(spec["sidestep_root"]).resolve()
    checkpoints = Path(spec["checkpoint_dir"]).resolve()
    dataset = Path(spec["dataset_dir"]).resolve()
    output = Path(spec["output_dir"]).resolve()
    if not spec.get("rights_confirmed") or not spec.get("consent_confirmed"):
        raise ValueError("Training data rights and performer consent required")
    if not (root / "train.py").is_file():
        raise ValueError("Official Side-Step train.py not found")
    if not checkpoints.is_dir() or not dataset.is_dir():
        raise ValueError("Model checkpoints and preprocessed dataset must exist")
    if not any(dataset.iterdir()):
        raise ValueError("Preprocessed dataset directory is empty")
    if output == root or output == dataset or output == checkpoints:
        raise ValueError("Training output must be separate from source data")
    variant = spec.get("model_variant", "base")
    if variant not in ("base", "sft", "turbo"):
        raise ValueError("Unsupported model variant")
    epochs = spec.get("epochs", 10)
    if type(epochs) is not int or not 1 <= epochs <= 1000:
        raise ValueError("Invalid epoch count")
    lr = spec.get("learning_rate", 0.0001)
    if type(lr) not in (float, int) or not 1e-7 <= lr <= 0.01:
        raise ValueError("Invalid learning rate")
    return {
        "status": "READY_TO_TRAIN_NOT_STARTED",
        "cwd": str(root),
        "command": [
            "uv", "run", "python", "train.py", "fixed",
            "--checkpoint-dir", str(checkpoints),
            "--model-variant", variant,
            "--dataset-dir", str(dataset),
            "--output-dir", str(output),
            "--epochs", str(epochs),
            "--lr", str(lr),
        ],
        "model_variant": variant,
        "training_rights_confirmed": True,
        "consent_confirmed": True,
        "evidence": {
            "training_executed": False,
            "checkpoint_verified": False,
            "audio_inference_verified": False,
            "android_integration_verified": False,
        },
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("config", type=Path)
    parser.add_argument("--execute", action="store_true",
                        help="Run the locally installed official Side-Step trainer")
    args = parser.parse_args()
    spec = json.loads(args.config.read_text(encoding="utf-8"))
    plan = prepare(spec)
    output = Path(spec["output_dir"]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    (output / "ai_pulse_training_plan.json").write_text(
        json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    if args.execute:
        subprocess.run(plan["command"], cwd=plan["cwd"], check=True)
        plan["status"] = "TRAINING_COMMAND_EXITED_ZERO_CHECKPOINT_NOT_YET_VERIFIED"
        plan["evidence"]["training_executed"] = True
    print(json.dumps(plan, indent=2))


if __name__ == "__main__":
    main()
