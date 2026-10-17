"""AI Pulse verification helpers: mockable ACE-Step API, editing contract and PCM comparison."""
import hashlib,json,math,struct,time,urllib.parse,urllib.request,wave
from pathlib import Path
from dataclasses import dataclass
TASKS={"text2music","cover","repaint","lego","extract","complete"}
@dataclass(frozen=True)
class EditRequest:
    task:str
    prompt:str
    source_audio:str|None=None
    start:float|None=None
    end:float|None=None
    def validate(self):
        if self.task not in TASKS: raise ValueError("Unsupported task")
        if self.task!="text2music" and not self.source_audio: raise ValueError("Source audio required")
        if self.task=="repaint" and (self.start is None or self.end is None or not 0<=self.start<self.end):
            raise ValueError("Invalid repaint range")
        return self
    def params(self):
        self.validate()
        p={"task_type":self.task,"prompt":self.prompt,"audio_format":"wav"}
        if self.start is not None:p["repainting_start"]=self.start
        if self.end is not None:p["repainting_end"]=self.end
        return p
def verify_wav(path):
    with wave.open(str(path),"rb") as w:
        channels,rate,frames,width=w.getnchannels(),w.getframerate(),w.getnframes(),w.getsampwidth()
        if not (1<=channels<=2 and 8000<=rate<=192000 and width==2 and frames>0):
            raise ValueError("Unsupported PCM16 WAV")
        raw=w.readframes(frames)
    values=struct.unpack("<%dh"%(len(raw)//2),raw)
    peak=max(abs(v) for v in values)/32768
    rms=math.sqrt(sum(v*v for v in values)/len(values))/32768
    return {"duration_seconds":round(frames/rate,3),"sample_rate":rate,"channels":channels,
            "peak":round(peak,6),"rms":round(rms,6),
            "clipped_samples":sum(abs(v)>=32767 for v in values),
            "sha256":hashlib.sha256(Path(path).read_bytes()).hexdigest()}
def compare(a,b):
    left,right=verify_wav(a),verify_wav(b)
    return {"same_audio":left["sha256"]==right["sha256"],
            "rms_difference":round(abs(left["rms"]-right["rms"]),6),
            "duration_difference":round(abs(left["duration_seconds"]-right["duration_seconds"]),3),
            "a":left,"b":right}
class AceStepAPI:
    def __init__(self,base_url,token=""):
        parsed=urllib.parse.urlsplit(base_url)
        if parsed.scheme not in ("https","http") or not parsed.netloc or parsed.username or parsed.password:
            raise ValueError("Invalid URL")
        if parsed.scheme=="http" and parsed.hostname not in ("localhost","127.0.0.1","::1"):
            raise ValueError("Remote server requires HTTPS")
        self.base=base_url.rstrip("/")
        self.token=token
    def _request(self,path,body=None,binary=False):
        headers={"Accept":"audio/wav" if binary else "application/json"}
        if self.token:headers["Authorization"]="Bearer "+self.token
        data=None
        if body is not None:
            headers["Content-Type"]="application/json"
            data=json.dumps(body).encode()
        request=urllib.request.Request(self.base+path,data=data,headers=headers)
        with urllib.request.urlopen(request,timeout=30) as response:
            if urllib.parse.urlsplit(response.url).netloc!=urllib.parse.urlsplit(self.base).netloc:
                raise ValueError("Cross-origin redirect blocked")
            payload=response.read(200000001 if binary else 2000000)
        if binary:
            if len(payload)>200000000:raise ValueError("Audio too large")
            return payload
        result=json.loads(payload)
        if result.get("code",200)!=200:raise RuntimeError(str(result.get("error")))
        return result.get("data")
    def health(self):return self._request("/health")
    def generate(self,prompt,lyrics="",duration=30,timeout=120):
        if not 10<=duration<=600:raise ValueError("Duration out of range")
        job=self._request("/release_task",{"prompt":prompt,"lyrics":lyrics,
            "duration":duration,"audio_format":"wav","use_format":False,"thinking":True})
        task_id=job["task_id"]
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            state=self._request("/query_result",{"task_id_list":[task_id]})[0]
            if state["status"]==2:raise RuntimeError("Generation failed")
            if state["status"]==1:
                files=state["result"]
                if isinstance(files,str):files=json.loads(files)
                url=urllib.parse.urljoin(self.base+"/",files[0]["file"])
                origin,target=urllib.parse.urlsplit(self.base),urllib.parse.urlsplit(url)
                if (origin.scheme,origin.netloc)!=(target.scheme,target.netloc):
                    raise ValueError("Untrusted audio URL")
                return self._request(url[len(self.base):],binary=True)
            time.sleep(.1)
        raise TimeoutError("Generation timed out")
