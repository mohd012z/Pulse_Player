"""AI Pulse music research agent: evidence-first, rights-aware source discovery.

Reads official public APIs, indexes music model research metadata and produces
a review queue. Web content is untrusted and NEVER executed or used as training
audio automatically. Python standard library only.
"""
import argparse
import hashlib
import json
import re
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SEEDS = {
    "ace_step": "https://api.github.com/repos/ace-step/ACE-Step-1.5",
    "audiocraft": "https://api.github.com/repos/facebookresearch/audiocraft",
    "diffsinger": "https://api.github.com/repos/MoonInTheRiver/DiffSinger",
    "openutau": "https://api.github.com/repos/stakira/OpenUtau",
    "singing_vocoders": "https://api.github.com/repos/openvpi/SingingVocoders",
}
MAX_RESPONSE = 1024 * 1024


def fetch_metadata(url, *, timeout=15, opener=None):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != "api.github.com":
        raise ValueError("Only official GitHub API endpoints allowed")
    if not re.fullmatch(r"/repos/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", parsed.path):
        raise ValueError("Unexpected API path")
    request = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "AI-Pulse-Research-Agent/1.0",
    })
    transport = opener or urllib.request.urlopen
    with transport(request, timeout=timeout) as response:
        if urllib.parse.urlparse(response.geturl()).hostname != "api.github.com":
            raise ValueError("Unapproved API redirect")
        raw = response.read(MAX_RESPONSE + 1)
    if len(raw) > MAX_RESPONSE:
        raise ValueError("Metadata response too large")
    payload = json.loads(raw)
    if not isinstance(payload, dict):
        raise ValueError("Invalid API response")
    return payload


def analyze_record(name, payload):
    license_data = payload.get("license") or {}
    license_id = license_data.get("spdx_id") if isinstance(license_data, dict) else None
    return {
        "name": name,
        "repository": payload.get("html_url"),
        "default_branch": payload.get("default_branch"),
        "updated_at": payload.get("updated_at"),
        "description": str(payload.get("description") or "")[:500],
        "source_license_spdx": license_id or "UNKNOWN",
        "weights_license": "UNVERIFIED",
        "training_data_rights": "UNVERIFIED",
        "model_inference": "UNVERIFIED",
        "source_revision_pinned": False,
        "review_required": True,
    }


def research(names=None, *, fetcher=fetch_metadata):
    names = list(SEEDS) if names is None else names
    if not names or any(name not in SEEDS for name in names):
        raise ValueError("Invalid research selection")
    results = []
    for name in names:
        try:
            data = fetcher(SEEDS[name])
            results.append({"status": "METADATA_RETRIEVED", **analyze_record(name, data)})
        except (OSError, ValueError, KeyError, TimeoutError) as error:
            results.append({"name": name, "status": "FETCH_FAILED",
                            "error": type(error).__name__, "review_required": True})
    return {
        "agent": "AI_PULSE_RESEARCH_V1",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "status": "RESEARCH_ONLY_NOT_TRAINED",
        "sources": results,
        "next_steps": [
            "Review official repository and source license",
            "Review model card, weights license and dataset rights separately",
            "Pin immutable source and model revisions",
            "Acquire only approved artifacts via model_acquisition.py",
            "Use training_agent.py only with authorized training data",
            "Evaluate real generated audio before Android release",
        ],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", nargs="*", choices=sorted(SEEDS))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = research(args.sources or None)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "sources": len(report["sources"]),
                      "fetched": sum(s["status"] == "METADATA_RETRIEVED" for s in report["sources"])}))


if __name__ == "__main__":
    main()
