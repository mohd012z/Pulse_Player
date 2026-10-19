#!/usr/bin/env python3
"""Read-only availability monitor for approved, public provider endpoints.

Configuration: PULSE_PROVIDER_HEALTH_JSON is a JSON array of
{"id":"provider-id","url":"https://official.example/status"}.
No MovieBox private endpoints, auth tokens, scraping or playback circumvention.
"""
import json, os, sys, urllib.request, urllib.parse, ipaddress, socket

def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Only credential-free HTTPS endpoints are allowed")
    host = parsed.hostname
    if host.lower() in ("localhost",) or host.lower().endswith((".local", ".internal", ".localhost")):
        raise ValueError("Private hosts are prohibited")
    for info in socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM):
        address = ipaddress.ip_address(info[4][0])
        if not address.is_global:
            raise ValueError("Non-public IP addresses are prohibited")
    return parsed

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl)
        if urllib.parse.urlsplit(newurl).hostname != urllib.parse.urlsplit(req.full_url).hostname:
            raise ValueError("Cross-host redirects require separate approval")
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def classify(status):
    if status in (401, 403, 451):
        return "authorization_or_policy_change"
    if status in (404, 410):
        return "endpoint_removed_or_moved"
    if status == 429:
        return "rate_limited"
    if status >= 500:
        return "provider_unavailable"
    return "unexpected_http_status"

def check(entry):
    url = entry["url"]
    validate_url(url)
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent":"PulsePlayer-ProviderHealth/1.0"})
    opener = urllib.request.build_opener(SafeRedirect())
    with opener.open(req, timeout=12) as response:
        final = urllib.parse.urlsplit(response.url)
        if final.scheme != "https":
            raise ValueError("Redirect to non-HTTPS endpoint")
        return {"id":entry["id"],"status":response.status,"ok":200 <= response.status < 400, "diagnosis": "healthy" if response.status < 400 else classify(response.status)}

def main():
    config = json.loads(os.environ.get("PULSE_PROVIDER_HEALTH_JSON","[]"))
    if not isinstance(config,list) or len(config)>20:
        raise ValueError("Expected at most 20 approved provider status URLs")
    if not config:
        print("No approved provider endpoints configured; monitoring is inactive")
        with open("provider-health.json","w") as out: json.dump({"configured":False,"results":[]},out)
        return
    results=[]
    for item in config:
        try: results.append(check(item))
        except urllib.error.HTTPError as exc: results.append({"id":item.get("id","unknown"),"ok":False,"status":exc.code,"diagnosis":classify(exc.code)})
        except Exception as exc: results.append({"id":item.get("id","unknown"),"ok":False,"diagnosis":"network_or_validation_failure","error":type(exc).__name__})
    print(json.dumps(results,indent=2))
    with open("provider-health.json","w") as out: json.dump(results,out,indent=2)
    if any(not item["ok"] for item in results): sys.exit(1)

if __name__=="__main__": main()
