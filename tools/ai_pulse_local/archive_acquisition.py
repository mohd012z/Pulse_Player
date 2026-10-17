"""Acquire pinned public source archives from official GitHub endpoints.

Source ZIP only; never auto-download model weights. Supports verified local
cache and safe extraction. Requires explicit source-license review.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import urllib.request
import zipfile

SOURCES = {
    "ace_step": "ace-step/ACE-Step-1.5",
    "audiocraft": "facebookresearch/audiocraft",
    "diffsinger": "SleePerwtm/DiffSinger-openvpi",
    "singing_vocoders": "openvpi/SingingVocoders",
}
MAX_ARCHIVE = 250 * 1024 * 1024
MAX_UNPACKED = 500 * 1024 * 1024
MAX_FILES = 25000


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def urls(name, revision):
    if name not in SOURCES:
        raise ValueError("Unknown source")
    if len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision):
        raise ValueError("Require immutable 40-character lowercase commit SHA")
    slug = SOURCES[name]
    return [
        f"https://codeload.github.com/{slug}/zip/{revision}",
        f"https://github.com/{slug}/archive/{revision}.zip",
    ]


def safe_extract(archive, destination):
    destination = Path(destination).resolve()
    if destination.exists():
        raise FileExistsError(destination)
    with zipfile.ZipFile(archive) as zf:
        members = zf.infolist()
        if len(members) > MAX_FILES or sum(i.file_size for i in members) > MAX_UNPACKED:
            raise ValueError("Archive extraction limit exceeded")
        root_names = set()
        for info in members:
            path = PurePosixPath(info.filename)
            if path.is_absolute() or ".." in path.parts or not path.parts or "\\" in info.filename:
                raise ValueError("Unsafe ZIP path")
            if info.external_attr >> 16 & 0o170000 == 0o120000:
                raise ValueError("ZIP symbolic links not permitted")
            root_names.add(path.parts[0])
        if len(root_names) != 1:
            raise ValueError("Expected a single source archive root")
        destination.mkdir(parents=True)
        try:
            for info in members:
                parts = PurePosixPath(info.filename).parts[1:]
                if not parts:
                    continue
                target = destination.joinpath(*parts)
                if info.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with zf.open(info) as source, target.open("xb") as out:
                    shutil.copyfileobj(source, out, 1024 * 1024)
        except BaseException:
            shutil.rmtree(destination)
            raise


def acquire(name, revision, destination, cache, *, license_reviewed=False,
            expected_sha256=None):
    if not license_reviewed:
        raise ValueError("Explicit source-license review required")
    sources = urls(name, revision)
    destination = Path(destination).resolve()
    cache = Path(cache).resolve()
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / f"{name}-{revision}.zip"
    if archive.is_symlink():
        raise ValueError("Symlinked cache entries not allowed")
    if archive.exists() and archive.stat().st_size > MAX_ARCHIVE:
        raise ValueError("Cached archive exceeds limit")
    if not archive.exists():
        errors = []
        for url in sources:
            temp = archive.with_suffix(".partial")
            temp.unlink(missing_ok=True)
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "AI-Pulse-Source-Archive/1.0"})
                with urllib.request.urlopen(request, timeout=45) as response, temp.open("xb") as output:
                    total = 0
                    while True:
                        block = response.read(1024 * 1024)
                        if not block:
                            break
                        total += len(block)
                        if total > MAX_ARCHIVE:
                            raise ValueError("Source ZIP exceeds maximum size")
                        output.write(block)
                if not zipfile.is_zipfile(temp):
                    raise ValueError("Downloaded content is not a ZIP archive")
                temp.replace(archive)
                break
            except Exception as error:
                errors.append(f"{url}: {type(error).__name__}")
                temp.unlink(missing_ok=True)
        if not archive.exists():
            raise RuntimeError("All official archive endpoints failed: " + "; ".join(errors))
    checksum = sha256(archive)
    if expected_sha256 and checksum.lower() != expected_sha256.lower():
        raise ValueError("Archive SHA-256 mismatch")
    safe_extract(archive, destination)
    manifest = {
        "source": SOURCES[name], "revision": revision,
        "archive_sha256": checksum, "archive_path": str(archive),
        "license_reviewed_by_operator": True,
        "model_weights_included": False,
        "status": "SOURCE_ARCHIVE_EXTRACTED_NOT_EXECUTED",
    }
    (destination / "AI_PULSE_ARCHIVE_MANIFEST.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name", choices=sorted(SOURCES))
    parser.add_argument("--revision", required=True)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=Path(".ai_pulse_archive_cache"))
    parser.add_argument("--expected-sha256")
    parser.add_argument("--license-reviewed", action="store_true")
    args = parser.parse_args()
    print(json.dumps(acquire(args.name, args.revision, args.destination,
                             args.cache, license_reviewed=args.license_reviewed,
                             expected_sha256=args.expected_sha256), indent=2))


if __name__ == "__main__":
    main()
