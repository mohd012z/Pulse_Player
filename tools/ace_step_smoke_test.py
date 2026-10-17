#!/usr/bin/env python3
"""ACE-Step 1.5 end-to-end proof: health -> submit -> poll -> WAV download.
Requires an independently running, authorized ACE-Step API server.
Usage: ACESTEP_URL=https://your-server.example python tools/ace_step_smoke_test.py
"""
import json
import os
import pathlib
import struct
import sys
import time
import urllib.parse
import urllib.request

base = os.environ.get("ACESTEP_URL", "").rstrip("/")
if not base.startswith("https://"):
    sys.exit("Set ACESTEP_URL to an HTTPS ACE-Step server (no credentials in URL).")
token = os.environ.get("ACESTEP_API_KEY", "")
def call(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)

health = call("/health")
print("HEALTH:", json.dumps(health)[:300])
job = call("/release_task", {
    "caption": "Original bright acoustic pop, warm guitar, piano, bass, steady drums, expressive original vocal",
    "lyrics": "[Verse]\nMorning light across the sky\nNew ideas are passing by\n[Chorus]\nMake a melody today\nLet the music lead the way",
    "duration": 30, "use_format": "wav",
})
task_id = (job.get("data") or {}).get("task_id")
if not task_id:
    sys.exit("FAIL: server did not return task_id")
print("TASK:", task_id)
deadline = time.monotonic() + 720
while time.monotonic() < deadline:
    status = call("/query_result", {"task_id_list": [task_id]})
    entries = status.get("data") or []
    if not entries:
        sys.exit("FAIL: empty status response")
    entry = entries[0]
    state = entry.get("status")
    if state == 2:
        sys.exit("FAIL: generation failed: " + str(entry))
    if state == 1:
        result = entry.get("result")
        files = json.loads(result) if isinstance(result, str) else result
        path = files[0]["file"]
        url = urllib.parse.urljoin(base + "/", path)
        origin = urllib.parse.urlsplit(base)
        target = urllib.parse.urlsplit(url)
        if (origin.scheme, origin.netloc) != (target.scheme, target.netloc):
            sys.exit("FAIL: audio URL redirects to an unexpected host")
        output = pathlib.Path("ace_step_proof.wav")
        request = urllib.request.Request(url, headers={"Authorization": "Bearer " + token} if token else {})
        with urllib.request.urlopen(request, timeout=120) as response:
            with output.open("wb") as dest:
                while chunk := response.read(65536):
                    dest.write(chunk)
                    if dest.tell() > 200_000_000:
                        sys.exit("FAIL: audio too large")
        with output.open("rb") as stream:
            header = stream.read(12)
        if len(header) != 12 or header[:4] != b"RIFF" or header[8:12] != b"WAVE":
            output.unlink(missing_ok=True)
            sys.exit("FAIL: generated file is not a RIFF/WAVE file")
        print("PASS:", output, "bytes:", output.stat().st_size)
        sys.exit(0)
    print("WAIT:", state)
    time.sleep(3)
sys.exit("FAIL: generation timed out")
