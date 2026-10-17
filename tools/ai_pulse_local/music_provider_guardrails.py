"""AI Pulse provider guardrails: auditable music-generation request planning.

No scraping of proprietary services, bypassing subscriptions, or imitation
of a real singer. Backends must be separately installed and licensed.
"""
import argparse
import json
import re
from dataclasses import dataclass, asdict

BACKENDS = {
    "ace_step": {"kind": "local_or_self_hosted", "source": "https://github.com/ace-step/ACE-Step-1.5", "weight_license": "VERIFY_MODEL_CARD"},
    "musicgen": {"kind": "local_or_self_hosted", "source": "https://github.com/facebookresearch/audiocraft", "weight_license": "CC-BY-NC-4.0"},
    "stable_audio": {"kind": "licensed_service_or_self_hosted", "source": "https://stability.ai/license", "weight_license": "VERIFY_MODEL_AND_LICENSE"},
    "diffsinger": {"kind": "voicebank_inference", "source": "https://github.com/openvpi/DiffSinger", "weight_license": "VERIFY_VOICEBANK_AND_VOCODER"},
}
BLOCKED = [
    r"\\b(?:clone|imitate|impersonate)\\s+(?:the\\s+)?voice\\s+of\\b",
    r"\\b(?:sound exactly like|identical to the voice of)\\b",
]


def build_plan(payload):
    backend = payload.get("backend")
    if backend not in BACKENDS:
        raise ValueError("Unknown provider: no unverified download or API access")
    prompt = payload.get("prompt")
    if not isinstance(prompt, str) or not 1 <= len(prompt.strip()) <= 2000:
        raise ValueError("Prompt must contain 1-2000 characters")
    if any(re.search(rule, prompt, flags=re.I) for rule in BLOCKED):
        raise ValueError("Nonconsensual singer impersonation request blocked")
    seconds = payload.get("duration_seconds", 30)
    if type(seconds) not in (int, float) or not 1 <= seconds <= 360:
        raise ValueError("Duration outside supported range")
    if payload.get("reference_audio") and not payload.get("reference_audio_authorized"):
        raise ValueError("Reference audio requires authorization")
    if not payload.get("license_reviewed"):
        raise ValueError("Model/voicebank license review required")
    if payload.get("distribution") == "commercial" and backend == "musicgen":
        raise ValueError("MusicGen pretrained weights are noncommercial")
    return {
        "status": "PLANNED_NOT_GENERATED",
        "backend": backend,
        "source": BACKENDS[backend]["source"],
        "weight_license": BACKENDS[backend]["weight_license"],
        "prompt": prompt.strip(),
        "duration_seconds": seconds,
        "lyrics": payload.get("lyrics", ""),
        "reference_audio_authorized": bool(payload.get("reference_audio_authorized")),
        "requires_installed_backend": True,
        "requires_model_signature_validation": True,
        "requires_output_audio_validation": True,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("request", help="JSON music-generation request")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    with open(args.request, encoding="utf-8") as src:
        plan = build_plan(json.load(src))
    with open(args.output, "w", encoding="utf-8") as dst:
        json.dump(plan, dst, indent=2, ensure_ascii=False)
        dst.write("\\n")
    print(json.dumps({"status": plan["status"], "backend": plan["backend"]}))


if __name__ == "__main__":
    main()
