"""P0-P4 upstream architecture manifest and reproducible source audit.

This utility DOES NOT download model weights or claim trained inference.
Run with --clone to checkout pinned upstream source revisions after reviewing licenses.
"""
import argparse
import json
import subprocess
from pathlib import Path

SOURCES = {
    "openutau": {
        "url": "https://github.com/openutau/OpenUtau.git",
        "role": ["P0", "P1"],
        "license_review": "Verify repository and voicebank license separately",
    },
    "amphion": {
        "url": "https://github.com/open-mmlab/Amphion.git",
        "role": ["P2", "P4"],
        "license_review": "MIT source; verify models, datasets and dependencies",
    },
    "ace_step": {
        "url": "https://github.com/ace-step/ACE-Step-1.5.git",
        "role": ["P3", "P4"],
        "license_review": "MIT source; verify model-weight terms separately",
    },
}


def run(args):
    root = args.destination.resolve()
    root.mkdir(parents=True, exist_ok=True)
    report = {}
    for name, spec in SOURCES.items():
        folder = root / name
        item = dict(spec)
        if args.clone:
            if folder.exists():
                raise RuntimeError(f"Refusing to overwrite {folder}")
            subprocess.run(["git", "clone", "--depth", "1", spec["url"], str(folder)], check=True)
            item["commit"] = subprocess.check_output(
                ["git", "-C", str(folder), "rev-parse", "HEAD"], text=True
            ).strip()
            item["license_files"] = [
                str(path.relative_to(folder))
                for path in folder.iterdir()
                if path.is_file() and "license" in path.name.lower()
            ]
        report[name] = item
    output = root / "upstream_audit.json"
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", type=Path, default=Path("upstream_sources"))
    parser.add_argument("--clone", action="store_true", help="Clone source repositories")
    run(parser.parse_args())
