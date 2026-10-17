"""AI Pulse training planner for licensed, consented music datasets.

Generates conservative ACE-Step Side-Step commands, without executing code,
downloading weights, or claiming trained checkpoints.
"""
import argparse
import json
from pathlib import Path

PROFILES = {
    "low_vram": {"rank": 16, "batch_size": 1, "grad_accum": 8, "optimizer": "adafactor"},
    "balanced": {"rank": 64, "batch_size": 1, "grad_accum": 4, "optimizer": "adamw8bit"},
    "quality": {"rank": 128, "batch_size": 1, "grad_accum": 4, "optimizer": "adamw"},
}


def plan_training(spec):
    profile = spec.get("profile", "balanced")
    if profile not in PROFILES:
        raise ValueError("Unknown training profile")
    if not spec.get("rights_confirmed") or not spec.get("voice_consent_confirmed"):
        raise ValueError("Document training rights and voice consent before proceeding")
    count = spec.get("audio_count")
    if type(count) is not int or count < 1:
        raise ValueError("Positive audio_count required")
    if spec.get("base_variant", "base") not in ("base", "turbo", "sft"):
        raise ValueError("Unsupported base variant")
    config = PROFILES[profile].copy()
    epochs = 200 if count < 10 else 100 if count < 50 else 50
    steps_per_epoch = (count + config["batch_size"] * config["grad_accum"] - 1) // (
        config["batch_size"] * config["grad_accum"])
    return {
        "status": "PLAN_ONLY_NOT_TRAINED",
        "framework": "ACE-Step Side-Step corrected LoRA",
        "profile": profile,
        "base_variant": spec.get("base_variant", "base"),
        "rank": config["rank"],
        "optimizer": config["optimizer"],
        "batch_size": config["batch_size"],
        "gradient_accumulation": config["grad_accum"],
        "effective_batch_size": config["batch_size"] * config["grad_accum"],
        "suggested_epochs": epochs,
        "estimated_optimizer_steps": epochs * steps_per_epoch,
        "learning_rate": 0.0001,
        "preprocess_once": True,
        "recommended_gates": [
            "license and recording-consent evidence",
            "holdout songs and duplicate detection",
            "cache audio latents and text embeddings",
            "train small LoRA first and measure peak GPU memory",
            "compare validation loss and audio samples",
            "stop early if validation quality declines",
            "record source revision, checkpoint hash and GPU timing",
        ],
        "notes": "Profiles are planning suggestions, not validated upstream CLI flags. Use official Side-Step wizard for exact settings.",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("spec", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = plan_training(json.loads(args.spec.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "estimated_optimizer_steps": result["estimated_optimizer_steps"]}))


if __name__ == "__main__":
    main()
