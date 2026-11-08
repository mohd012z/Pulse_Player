import importlib.util,pathlib,tempfile,unittest
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
   path=pathlib.Path(d)/"jobs.db";a=m.Journal(path);a.enqueue("j");self.assertTrue(a.claim("j","a",100,10))
   b=m.Journal(path);self.assertFalse(b.claim("j","b",105,10));self.assertTrue(b.claim("j","b",110,10))
   self.assertFalse(a.finish("j","a",111));self.assertTrue(b.finish("j","b",111))
 def test_no_media(self):
  r=m.analyze(sample());self.assertFalse(r["media_accessed"]);self.assertFalse(r["media_modified"])
if __name__=="__main__":unittest.main()
