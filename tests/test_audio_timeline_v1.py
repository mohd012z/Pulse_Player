import importlib.util,pathlib,tempfile,unittest,json,threading,sqlite3
p=pathlib.Path(__file__).resolve().parents[1]/"scripts/audio_timeline_v1.py"
s=importlib.util.spec_from_file_location("timeline",p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
def sample(text="Welcome to the show"):
 return {"source_sha256":"a"*64,"duration_ms":3000,"segments":[{"id":"s1","start_ms":0,"end_ms":1000,"text":text}]}
class TimelineTests(unittest.TestCase):
 def test_intro(self):self.assertEqual(m.analyze(sample())["findings"][0]["classification"],"intro")
 def test_ad(self):self.assertEqual(m.classify("Sponsored by X"),"ad")
 def test_promo(self):self.assertEqual(m.classify("Visit our merch store"),"promo")
 def test_outro(self):self.assertEqual(m.classify("Thanks for listening"),"outro")
 def test_content(self):self.assertEqual(m.classify("An interview about science"),"content")
 def test_empty_uncertain(self):self.assertEqual(m.classify(""),"uncertain")
 def test_mixed_uncertain(self):self.assertEqual(m.classify("Welcome to the show, sponsored by X"),"uncertain")
 def test_overlap(self):
  d=sample();d["segments"].append({"id":"s2","start_ms":900,"end_ms":2000,"text":"x"})
  with self.assertRaises(ValueError):m.analyze(d)
 def test_duplicate(self):
  d=sample();d["segments"].append({"id":"s1","start_ms":1000,"end_ms":2000,"text":"x"})
  with self.assertRaises(ValueError):m.analyze(d)
 def test_bool_timestamp(self):
  d=sample();d["segments"][0]["start_ms"]=False
  with self.assertRaises(ValueError):m.analyze(d)
 def test_deterministic(self):self.assertEqual(m.analyze(sample()),m.analyze(sample()))
 def test_provenance(self):self.assertEqual(m.analyze(sample())["source_sha256"],"a"*64)
 def test_immutable(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/"report.json";m.publish(m.analyze(sample()),p)
   with self.assertRaises(FileExistsError):m.publish(m.analyze(sample()),p)
 def test_restart(self):
  with tempfile.TemporaryDirectory() as d:
   path=pathlib.Path(d)/"jobs.db";a=m.Journal(path);a.enqueue("j");token_a=a.claim("j","a",100,10);self.assertTrue(token_a)
   b=m.Journal(path);self.assertFalse(b.claim("j","b",105,10));token_b=b.claim("j","b",110,10);self.assertTrue(token_b)
   self.assertFalse(a.finish("j",token_a,111));self.assertTrue(b.finish("j",token_b,111))
 def test_no_media(self):
  r=m.analyze(sample());self.assertFalse(r["media_accessed"]);self.assertFalse(r["media_modified"])
 def test_same_owner_aba_fencing(self):
  with tempfile.TemporaryDirectory() as d:
   j=m.Journal(pathlib.Path(d)/"jobs.db");j.enqueue("same")
   first=j.claim("same","worker",10,5)
   second=j.claim("same","worker",15,5)
   self.assertNotEqual(first,second)
   self.assertFalse(j.finish("same",first,16))
   self.assertTrue(j.finish("same",second,16))
 def test_concurrent_claim_one_winner(self):
  with tempfile.TemporaryDirectory() as d:
   db=pathlib.Path(d)/"jobs.db";j=m.Journal(db);j.enqueue("race")
   results=[];lock=threading.Lock();gate=threading.Barrier(8)
   def worker(i):
    gate.wait()
    token=m.Journal(db).claim("race","w"+str(i),100,30)
    with lock:results.append(token)
   threads=[threading.Thread(target=worker,args=(i,)) for i in range(8)]
   for t in threads:t.start()
   for t in threads:t.join(timeout=10)
   self.assertTrue(all(not t.is_alive() for t in threads))
   self.assertEqual(sum(bool(x) for x in results),1)
 def test_report_integrity(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/"report.json";m.publish(m.analyze(sample()),p)
   report=json.loads(p.read_text())
   self.assertTrue(m.verify(report))
   report["findings"][0]["classification"]="ad"
   self.assertFalse(m.verify(report))
 def test_report_rejects_overwrite_unchanged(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/"report.json";m.publish(m.analyze(sample()),p);original=p.read_bytes()
   with self.assertRaises(FileExistsError):m.publish(m.analyze(sample("Different")),p)
   self.assertEqual(p.read_bytes(),original)
 def test_expired_token_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   j=m.Journal(pathlib.Path(d)/"jobs.db");j.enqueue("j");token=j.claim("j","w",10,5)
   self.assertFalse(j.finish("j",token,15))
 def test_restart_complete_not_reclaimed(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/"jobs.db";j=m.Journal(p);j.enqueue("j");token=j.claim("j","w",10,5)
   self.assertTrue(j.finish("j",token,11))
   self.assertFalse(m.Journal(p).claim("j","new",100,5))
if __name__=="__main__":unittest.main()
