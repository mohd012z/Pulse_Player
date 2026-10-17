"""Acquire official AI Pulse model artifacts with explicit provenance.

Only approved Hugging Face repositories. No remote Python execution.
Weights are never deserialized by this utility.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath

APPROVED = {
    "ace_step": "ACE-Step/Ace-Step1.5",
    "musicgen_small": "facebook/musicgen-small",
}
ALLOWED_SUFFIXES = (".safetensors", ".json", ".txt", ".model", ".md", ".yaml", ".yml")
EXCLUDED = ("*.bin", "*.pkl", "*.pickle", "*.pt", "*.pth", "*.py", "*.so", "*.dll", "*.exe")


def verify_revision(revision):
    if not isinstance(revision, str) or len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision):
        raise ValueError("Pin an immutable lowercase 40-character revision SHA")
    return revision


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def acquire(name, revision, output, *, license_reviewed=False, max_gib=32):
    if name not in APPROVED:
        raise ValueError("Unapproved model repository")
    verify_revision(revision)
    if not license_reviewed:
        raise ValueError("Review the model-card and weight license first")
    if not 1 <= max_gib <= 128:
        raise ValueError("Invalid download limit")
    from huggingface_hub import HfApi, snapshot_download
    repo = APPROVED[name]
    api = HfApi()
    info = api.model_info(repo_id=repo, revision=revision, files_metadata=True)
    selected = []
    total = 0
    for item in info.siblings:
        filename = item.rfilename
        path = PurePosixPath(filename)
        if path.is_absolute() or ".." in path.parts or "\\" in filename or path.suffix.lower() not in ALLOWED_SUFFIXES:
            continue
        if filename.endswith(".py"):
            continue
        selected.append(filename)
        size = getattr(item, "size", None)
        if size is None:
            raise ValueError("Missing file size: cannot enforce download budget")
        total += size
    if not selected or total > max_gib * (1024 ** 3):
        raise ValueError("No approved files or model exceeds download budget")
    output = Path(output).resolve()
    if output.exists():
        raise FileExistsError("Destination must be new")
    snapshot_download(
        repo_id=repo, revision=revision, local_dir=str(output),
        allow_patterns=selected, ignore_patterns=list(EXCLUDED),
    )
    records = []
    for filename in selected:
        path = output / filename
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(output):
            raise ValueError("Missing or symlinked downloaded file")
        records.append({"path": filename, "bytes": path.stat().st_size, "sha256": sha256(path)})
    manifest = {"repository": repo, "revision": revision, "files": records,
                "license_reviewed_by_operator": True,
                "status": "DOWNLOADED_NOT_INFERENCE_VERIFIED"}
    (output / "AI_PULSE_MODEL_MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name", choices=sorted(APPROVED))
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--max-gib", type=int, default=32)
    parser.add_argument("--license-reviewed", action="store_true")
    args = parser.parse_args()
    result = acquire(args.name, args.revision, args.output,
                     license_reviewed=args.license_reviewed, max_gib=args.max_gib)
    print(json.dumps({"status": result["status"], "files": len(result["files"])}, indent=2))


if __name__ == "__main__":
    main()
