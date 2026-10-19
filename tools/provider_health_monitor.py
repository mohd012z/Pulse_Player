#!/usr/bin/env python3
"""Read-only availability monitor for approved, public provider endpoints.

Configuration: PULSE_PROVIDER_HEALTH_JSON is a JSON array of
{"id":"provider-id","url":"https://official.example/status"}.
No MovieBox private endpoints, auth tokens, scraping or playback circumvention.
"""
import json, os, sys, urllib.request, urllib.parse

def check(entry):
    url = entry["url"]
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Only credential-free HTTPS status endpoints are allowed")
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent":"PulsePlayer-ProviderHealth/1.0"})
    with urllib.request.urlopen(req, timeout=12) as response:
        final = urllib.parse.urlsplit(response.url)
        if final.scheme != "https":
            raise ValueError("Redirect to non-HTTPS endpoint")
        return {"id":entry["id"],"status":response.status,"ok":200 <= response.status < 400}

def main():
    config = json.loads(os.environ.get("PULSE_PROVIDER_HEALTH_JSON","[]"))
    if not isinstance(config,list) or len(config)>20:
        raise ValueError("Expected at most 20 approved provider status URLs")
    results=[]
    for item in config:
        try: results.append(check(item))
        except Exception as exc: results.append({"id":item.get("id","unknown"),"ok":False,"error":type(exc).__name__})
    print(json.dumps(results,indent=2))
    with open("provider-health.json","w") as out: json.dump(results,out,indent=2)
    if any(not item["ok"] for item in results): sys.exit(1)

if __name__=="__main__": main()
