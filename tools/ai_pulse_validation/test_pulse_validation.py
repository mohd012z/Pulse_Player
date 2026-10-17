import io,json,math,struct,tempfile,threading,unittest,wave
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from pulse_validation import AceStepAPI,EditRequest,verify_wav,compare
def wav_bytes(amplitude=2000):
    raw=struct.pack("<8000h",*[int(amplitude*math.sin(i*math.pi/20)) for i in range(8000)])
    out=io.BytesIO()
    with wave.open(out,"wb") as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(8000);w.writeframes(raw)
    return out.getvalue()
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def reply(self,obj):
        data=json.dumps(obj).encode()
        self.send_response(200);self.send_header("Content-Type","application/json")
        self.end_headers();self.wfile.write(data)
    def do_GET(self):
        if self.path=="/health":return self.reply({"code":200,"data":{"status":"ok"}})
        if self.path.startswith("/v1/audio?"):
            data=wav_bytes();self.send_response(200);self.send_header("Content-Type","audio/wav")
            self.end_headers();self.wfile.write(data);return
        self.send_error(404)
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if self.path=="/release_task":
            assert body["audio_format"]=="wav" and body["use_format"] is False
            return self.reply({"code":200,"data":{"task_id":"mock-123"}})
        if self.path=="/query_result":
            assert body["task_id_list"]==["mock-123"]
            return self.reply({"code":200,"data":[{"status":1,"result":json.dumps([{"file":"/v1/audio?path=mock.wav"}])}]})
        self.send_error(404)
class TestPulse(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=ThreadingHTTPServer(("127.0.0.1",0),Handler)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close()
    def test_p0_mock_server_end_to_end(self):
        api=AceStepAPI("http://127.0.0.1:"+str(self.server.server_port))
        self.assertEqual(api.health()["status"],"ok")
        data=api.generate("original pop","original lyrics")
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"song.wav";p.write_bytes(data)
            self.assertEqual(verify_wav(p)["duration_seconds"],1.0)
    def test_p0_remote_http_rejected(self):
        with self.assertRaises(ValueError):AceStepAPI("http://example.com")
    def test_p0_blank_prompt_rejected(self):
        api=AceStepAPI("http://127.0.0.1:"+str(self.server.server_port))
        with self.assertRaises(ValueError):api.generate("   ")
    def test_p0_redirect_rejected(self):
        from pulse_validation import _NoRedirect
        handler=_NoRedirect()
        with self.assertRaises(ValueError):
            handler.redirect_request(None,None,302,"Redirect",{},"https://example.com/audio.wav")
    def test_p1_edit_contracts(self):
        with self.assertRaises(ValueError):EditRequest("repaint","edit").validate()
        with self.assertRaises(ValueError):EditRequest("repaint","edit","owned.wav",8,4).validate()
        self.assertEqual(EditRequest("repaint","edit","owned.wav",4,8).params()["repainting_end"],8)
        for task in ("cover","lego","extract","complete"):
            self.assertEqual(EditRequest(task,"original","owned.wav").validate().task,task)
    def test_p2_audio_quality_and_p3_reproducible_comparison(self):
        with tempfile.TemporaryDirectory() as tmp:
            a,b=Path(tmp)/"a.wav",Path(tmp)/"b.wav"
            a.write_bytes(wav_bytes(2000));b.write_bytes(wav_bytes(3500))
            self.assertEqual(verify_wav(a)["clipped_samples"],0)
            self.assertFalse(compare(a,b)["same_audio"])
            self.assertTrue(compare(a,a)["same_audio"])
if __name__=="__main__":unittest.main(verbosity=2)
