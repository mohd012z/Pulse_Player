"""Read-only offline transcript classification and SQLite job journal."""
import argparse,hashlib,json,os,re,sqlite3
from pathlib import Path
SCHEMA="pulse.audio-timeline.v1"
RULES={"ad":r"\b(sponsored by|use promo code|this episode is brought to you by)\b","promo":r"\b(subscribe to our newsletter|visit our merch store)\b","intro":r"\b(welcome to the show|you are listening to)\b","outro":r"\b(thanks for listening|see you next episode)\b"}
def canonical(x): return json.dumps(x,sort_keys=True,separators=(",",":"),ensure_ascii=False).encode()
def classify(text):
    matches=[k for k,v in RULES.items() if re.search(v,text,re.I)]
    return matches[0] if len(matches)==1 else "uncertain" if len(matches)>1 or not text.strip() else "content"
def analyze(doc):
    if not isinstance(doc,dict) or set(doc)!={"source_sha256","duration_ms","segments"}: raise ValueError("invalid schema")
    if not isinstance(doc["source_sha256"],str) or not re.fullmatch("[a-f0-9]{64}",doc["source_sha256"]): raise ValueError("invalid source SHA")
    duration=doc["duration_ms"]
    if type(duration)!=int or not 0<duration<=86400000: raise ValueError("invalid duration")
    segments=doc["segments"]
    if not isinstance(segments,list) or len(segments)>2000: raise ValueError("invalid count")
    prev=0;ids=set();findings=[]
    for s in segments:
        if not isinstance(s,dict) or set(s)!={"id","start_ms","end_ms","text"}: raise ValueError("invalid segment")
        ident,start,end,txt=s["id"],s["start_ms"],s["end_ms"],s["text"]
        if not isinstance(ident,str) or not re.fullmatch("[A-Za-z0-9_-]{1,64}",ident) or ident in ids: raise ValueError("invalid id")
        if type(start)!=int or type(end)!=int or not prev<=start<end<=duration: raise ValueError("invalid boundaries")
        if not isinstance(txt,str) or len(txt)>2000 or "\x00" in txt: raise ValueError("invalid text")
        ids.add(ident);prev=end
        findings.append({"id":ident,"start_ms":start,"end_ms":end,"classification":classify(txt),"action":"keep"})
    return {"schema":SCHEMA,"mode":"read-only","source_sha256":doc["source_sha256"],"transcript_sha256":hashlib.sha256(canonical(doc)).hexdigest(),"findings":findings,"media_accessed":False,"media_modified":False,"limitations":["User-provided transcript only","Heuristic matches do not prove advertising intent"]}
def publish(report,path):
    flags=os.O_CREAT|os.O_EXCL|os.O_WRONLY
    if hasattr(os,"O_NOFOLLOW"):flags|=os.O_NOFOLLOW
    fd=os.open(path,flags,0o600)
    try:
        with os.fdopen(fd,"wb") as f:
            f.write(canonical(report)+b"\n");f.flush();os.fsync(f.fileno())
    except BaseException:
        Path(path).unlink(missing_ok=True);raise
class Journal:
    def __init__(self,path):
        self.path=str(path)
        with self.connect() as db:db.execute("CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,state TEXT NOT NULL,owner TEXT,lease_until INTEGER NOT NULL DEFAULT 0)")
    def connect(self):return sqlite3.connect(self.path,timeout=5)
    def enqueue(self,ident):
        if not re.fullmatch("[A-Za-z0-9_-]{1,64}",ident):raise ValueError("invalid id")
        with self.connect() as db:db.execute("INSERT OR IGNORE INTO jobs(id,state) VALUES (?,?)",(ident,"queued"))
    def claim(self,ident,owner,now,lease=60):
        if not re.fullmatch("[A-Za-z0-9_-]{1,64}",owner) or type(now)!=int or not 1<=lease<=3600:raise ValueError("invalid claim")
        with self.connect() as db:
            c=db.execute("UPDATE jobs SET state='running',owner=?,lease_until=? WHERE id=? AND (state='queued' OR (state='running' AND lease_until<=?))",(owner,now+lease,ident,now))
            return c.rowcount==1
    def finish(self,ident,owner,now):
        with self.connect() as db:
            c=db.execute("UPDATE jobs SET state='complete',lease_until=0 WHERE id=? AND state='running' AND owner=? AND lease_until>?",(ident,owner,now))
            return c.rowcount==1
def main():
    p=argparse.ArgumentParser();p.add_argument("transcript");p.add_argument("report");a=p.parse_args()
    source=Path(a.transcript)
    if source.is_symlink() or not source.is_file() or source.stat().st_size>1048576:raise ValueError("invalid input")
    publish(analyze(json.loads(source.read_text(encoding="utf-8"))),a.report)
if __name__=="__main__":main()
