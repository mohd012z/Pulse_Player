"""Pinned, license-reviewed research registry for AI Pulse neural engines.

No automatic weight download, execution, or copying of upstream source into
the Android APK. Produces an auditable study plan for authorized public repos.
"""
import argparse
import json
from pathlib import Path

RESEARCH = {
    "ace_step": {
        "repository": "https://github.com/ace-step/ACE-Step-1.5",
        "focus": ["PyTorch inference", "DiT", "VAE", "REST API", "GPU profiling"],
        "license_note": "MIT repository; separately inspect model assets",
        "integration": "GPU worker generates original music; Android receives WAV",
    },
    "audiocraft": {
        "repository": "https://github.com/facebookresearch/audiocraft",
        "focus": ["MusicGen", "audio tokens", "conditioning", "PyTorch inference"],
        "license_note": "MIT code; MusicGen pretrained weights CC BY-NC 4.0",
        "integration": "Optional noncommercial research provider",
    },
    "diffsinger": {
        "repository": "https://github.com/MoonInTheRiver/DiffSinger",
        "focus": ["phoneme alignment", "pitch conditioning", "diffusion acoustic model"],
        "license_note": "Review source and each dataset, checkpoint and voicebank separately",
        "integration": "Authorized note-controlled singing pipeline",
    },
    "openutau": {
        "repository": "https://github.com/openutau/OpenUtau",
        "focus": ["phonemizers", "MIDI editing", "DiffSinger rendering compatibility"],
        "license_note": "MIT repository; voicebanks have independent licenses",
        "integration": "Architecture reference for vocal editor and voicebank import",
    },
    "singing_vocoders": {
        "repository": "https://github.com/openvpi/SingingVocoders",
        "focus": ["NSF-HiFiGAN", "mel configuration", "sample-rate matching"],
        "license_note": "Check source, release weights and attribution independently",
        "integration": "Compatible vocoder only after verified tensor/audio contract",
    },
}

def build_study_plan(selected=None):
    names = list(RESEARCH) if selected is None else selected
    if not names or any(name not in RESEARCH for name in names):
        raise ValueError("Unknown or empty research selection")
    return {
        "status": "RESEARCH_PLAN_NOT_CLONED",
        "sources": [{"name": name, **RESEARCH[name]} for name in names],
        "verification_gates": [
            "official repository identity and pinned commit",
            "source and model-weight license review",
            "dependency and supply-chain review",
            "model architecture and input/output signature",
            "reproducible inference on authorized assets",
            "WAV integrity, latency, and perceptual evaluation",
            "Android integration and CI tests",
        ],
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", nargs="*", choices=sorted(RESEARCH))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    plan = build_study_plan(args.sources or None)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    print(f"Created study plan for {len(plan['sources'])} neural repositories")

if __name__ == "__main__":
    main()
