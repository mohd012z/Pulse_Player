#!/usr/bin/env python3
"""P0-P3 dependency-free ACE-Step contract tests; mock-only, not neural inference."""
import io
import json
import math
import struct
import threading
import unittest
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen

def test_wav():
    samples = [int(1200 * math.sin(i / 8)) for i in range(8000)]
    stream = io.BytesIO()
    with wave.open(stream, 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(8000)
        audio.writeframes(struct.pack('<8000h', *samples))
    return stream.getvalue()

class MockAceStep(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def send_json(self, obj):
        data = json.dumps({'code': 200, 'data': obj}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(data)
    def do_GET(self):
        if self.path == '/health':
            return self.send_json({'status': 'ok'})
        if self.path.startswith('/v1/audio?'):
            data = test_wav()
            self.send_response(200)
            self.send_header('Content-Type', 'audio/wav')
            self.end_headers()
            self.wfile.write(data)
        else:
            self.send_error(404)
    def do_POST(self):
        payload = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        if self.path == '/release_task':
            assert payload['audio_format'] == 'wav'
            assert isinstance(payload['use_format'], bool)
            self.send_json({'task_id': 'mock-123'})
        elif self.path == '/query_result':
            assert payload['task_id_list'] == ['mock-123']
            self.send_json([{'status': 1, 'result': json.dumps([{'file': '/v1/audio?path=mock.wav'}])}])
        else:
            self.send_error(404)

class AceStepContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), MockAceStep)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = 'http://127.0.0.1:' + str(cls.server.server_port)
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
    def post(self, path, data):
        request = Request(self.base + path, json.dumps(data).encode(),
                          {'Content-Type': 'application/json'})
        with urlopen(request, timeout=5) as response:
            return json.load(response)['data']
    def test_p0_submission_polling_download(self):
        with urlopen(self.base + '/health', timeout=5) as response:
            self.assertEqual(json.load(response)['data']['status'], 'ok')
        task = self.post('/release_task', {'prompt': 'original song', 'lyrics': 'original',
                         'audio_format': 'wav', 'use_format': False, 'thinking': True})
        self.assertEqual(task['task_id'], 'mock-123')
        status = self.post('/query_result', {'task_id_list': [task['task_id']]})[0]
        self.assertEqual(status['status'], 1)
        audio_path = json.loads(status['result'])[0]['file']
        with urlopen(self.base + audio_path, timeout=5) as response:
            output = response.read()
        self.assertEqual(output[:4], b'RIFF')
        with wave.open(io.BytesIO(output)) as audio:
            self.assertEqual(audio.getnframes(), 8000)
    def test_p1_edit_task_contracts(self):
        for task in ('cover', 'repaint', 'lego', 'extract', 'complete'):
            self.assertIn(task, {'cover', 'repaint', 'lego', 'extract', 'complete'})
        self.assertTrue(0 <= 4.0 < 8.0)
    def test_p2_pcm_metrics(self):
        with wave.open(io.BytesIO(test_wav())) as audio:
            raw = audio.readframes(audio.getnframes())
        samples = struct.unpack('<8000h', raw)
        peak = max(abs(x) for x in samples) / 32768
        rms = math.sqrt(sum(x*x for x in samples) / len(samples)) / 32768
        self.assertTrue(0 < rms <= peak < 1)
    def test_p3_deterministic_comparison(self):
        self.assertEqual(test_wav(), test_wav())
        self.assertNotEqual(test_wav(), test_wav() + b'X')
if __name__ == '__main__':
    unittest.main(verbosity=2)
