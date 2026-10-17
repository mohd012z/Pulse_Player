"""Reproducible source-blueprint retrieval with explicit licensing controls.

Downloads PUBLIC SOURCE CODE ONLY. Model weights/voicebanks are deliberately
excluded; review their separate licenses before acquisition.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

SOURCES = {
    "ace_step": {"url": "https://github.com/ace-step/ACE-Step-1.5.git", "license": "MIT", "role": "PyTorch music generation"},
    "audiocraft": {"url": "https://github.com/facebookresearch/audiocraft.git", "license": "MIT-code-only", "role": "MusicGen PyTorch architecture"},
    "diffsinger": {"url": "https://github.com/SleePerwtm/DiffSinger-openvpi.git", "license": "Apache-2.0-code-only", "role": "Neural singing and ONNX export"},
    "singing_vocoders": {"url": "https://github.com/openvpi/SingingVocoders.git", "license": "REVIEW-REPOSITORY-AND-WEIGHTS", "role": "Vocoder configuration reference"},
}
MAX_FILES = 20000


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def checkout(name, destination, *, acknowledge=False, revision=None):
    if name not in SOURCES:
        raise ValueError("Source not on official allowlist")
    if not acknowledge:
        raise ValueError("Review source license and acknowledge before downloading")
    destination = Path(destination).resolve()
    if destination.exists():
        raise FileExistsError(destination)
    if revision and (len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision)):
        raise ValueError("Revision must be a full 40-character lowercase Git SHA")
    destination.parent.mkdir(parents=True, exist_ok=True)
    source = SOURCES[name]
    try:
        subprocess.run(["git", "-c", "protocol.file.allow=never", "clone",
                        "--depth", "1", "--no-checkout", "--", source["url"],
                        str(destination)], check=True, timeout=300)
        if revision:
            subprocess.run(["git", "-C", str(destination), "fetch", "--depth", "1",
                            "origin", revision], check=True, timeout=300)
            subprocess.run(["git", "-C", str(destination), "checkout", "--detach",
                            revision], check=True, timeout=300)
        else:
            subprocess.run(["git", "-C", str(destination), "checkout", "--force"],
                           check=True, timeout=300)
        commit = subprocess.check_output(["git", "-C", str(destination), "rev-parse",
                                          "HEAD"], text=True, timeout=30).strip()
        tracked = subprocess.check_output(["git", "-C", str(destination), "ls-files",
                                           "-z"], timeout=30).split(b"\0")
        if len(tracked) > MAX_FILES:
            raise ValueError("Source tree exceeds file-count policy")
        license_files = []
        for candidate in ("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING"):
            path = destination / candidate
            if path.is_file() and not path.is_symlink():
                license_files.append({"path": candidate, "sha256": digest(path)})
        if not license_files:
            raise ValueError("No top-level license file: manual review required")
        manifest = {"name": name, "source": source["url"], "expected_license": source["license"],
                    "role": source["role"], "commit": commit, "license_files": license_files,
                    "weights_downloaded": False, "source_license_verified_manually": False,
                    "status": "SOURCE_RETRIEVED_LICENSE_REVIEW_PENDING"}
        (destination / "AI_PULSE_SOURCE_MANIFEST.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        return manifest
    except BaseException:
        # Preserve failed checkout for inspection; never silently erase arbitrary paths.
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name", choices=sorted(SOURCES))
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--revision")
    parser.add_argument("--acknowledge-source-license", action="store_true")
    args = parser.parse_args()
    print(json.dumps(checkout(args.name, args.destination,
                              acknowledge=args.acknowledge_source_license,
                              revision=args.revision), indent=2))


if __name__ == "__main__":
    main()
