"""Download official OpenVPI NSF-HiFiGAN dependency with provenance and safe extraction.

Only official GitHub API release assets; no mirrors, authentication bypass,
voicebank scraping, or automatic acceptance of licenses.
"""
import argparse
import hashlib
import json
import shutil
import urllib.request
import zipfile
from pathlib import Path

API = "https://api.github.com/repos/openvpi/vocoders/releases"
ALLOWED_REPO = "https://github.com/openvpi/vocoders/releases/download/"
MAX_DOWNLOAD = 512 * 1024 * 1024
MAX_UNPACKED = 1024 * 1024 * 1024


def request_json(url):
    req = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "AI-Pulse-vocoder-audit/1.0",
    })
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def discover(tag):
    releases = request_json(API)
    matches = [r for r in releases if r.get("tag_name") == tag]
    if len(matches) != 1:
        raise ValueError("Exact official release tag not found")
    release = matches[0]
    return [{
        "name": a["name"], "size": a["size"], "url": a["browser_download_url"]
    } for a in release.get("assets", [])
        if a.get("browser_download_url", "").startswith(ALLOWED_REPO)]


def safe_extract(archive, destination):
    destination = destination.resolve()
    with zipfile.ZipFile(archive) as z:
        files = [x for x in z.infolist() if not x.is_dir()]
        if sum(x.file_size for x in files) > MAX_UNPACKED:
            raise ValueError("Archive exceeds extracted size limit")
        for item in files:
            name = item.filename.replace("\\", "/")
            path = Path(name)
            if path.is_absolute() or ".." in path.parts or not name or ":" in name:
                raise ValueError("Unsafe archive path")
            # Zip Unix symlink flag
            if ((item.external_attr >> 16) & 0o170000) == 0o120000:
                raise ValueError("Symlink archive entry rejected")
            target = (destination / path).resolve()
            if not target.is_relative_to(destination):
                raise ValueError("Archive path escapes destination")
        for item in files:
            target = destination / item.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(item) as src, target.open("wb") as dst:
                shutil.copyfileobj(src, dst)


def download(asset, destination):
    if not asset["url"].startswith(ALLOWED_REPO):
        raise ValueError("Non-official source")
    if not (0 < asset["size"] <= MAX_DOWNLOAD):
        raise ValueError("Asset size outside safe limits")
    destination.mkdir(parents=True, exist_ok=True)
    filename = Path(asset["name"]).name
    if filename != asset["name"] or not filename.endswith((".oudep", ".zip")):
        raise ValueError("Unexpected asset name")
    target = destination / filename
    if target.exists():
        raise FileExistsError(target)
    digest = hashlib.sha256()
    written = 0
    try:
        req = urllib.request.Request(asset["url"], headers={"User-Agent": "AI-Pulse-vocoder-audit/1.0"})
        with urllib.request.urlopen(req, timeout=90) as response, target.open("xb") as stream:
            while block := response.read(1024 * 1024):
                written += len(block)
                if written > MAX_DOWNLOAD:
                    raise ValueError("Download exceeds size limit")
                digest.update(block)
                stream.write(block)
        if written != asset["size"]:
            raise ValueError("Release asset size mismatch")
        if not zipfile.is_zipfile(target):
            raise ValueError("Dependency is not a valid ZIP/OUDEP archive")
    except BaseException:
        target.unlink(missing_ok=True)
        raise
    evidence = {"source": asset["url"], "name": filename, "bytes": written,
                "sha256": digest.hexdigest(), "license_review": "REQUIRED",
                "model_inference_verified": False}
    (destination / (filename + ".provenance.json")).write_text(
        json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    return target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True, help="Exact official GitHub release tag")
    parser.add_argument("--asset", help="Exact asset filename; omitted to list release assets")
    parser.add_argument("--destination", type=Path, default=Path("downloaded_vocoders"))
    parser.add_argument("--extract", action="store_true")
    parser.add_argument("--acknowledge-license", action="store_true")
    args = parser.parse_args()
    assets = discover(args.tag)
    if not args.asset:
        print(json.dumps(assets, indent=2))
        return
    selected = [x for x in assets if x["name"] == args.asset]
    if len(selected) != 1:
        raise ValueError("Exact asset name not found")
    if not args.acknowledge_license:
        raise ValueError("Review upstream license and pass --acknowledge-license")
    archive = download(selected[0], args.destination)
    if args.extract:
        extracted = args.destination / (archive.name + ".unpacked")
        if extracted.exists():
            raise FileExistsError(extracted)
        extracted.mkdir(parents=True)
        try:
            safe_extract(archive, extracted)
        except BaseException:
            shutil.rmtree(extracted)
            raise
    print(archive)


if __name__ == "__main__":
    main()
