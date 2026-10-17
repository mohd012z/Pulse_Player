import json
import sys
import tempfile
import unittest
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from voice_dataset import inspect_dataset

class VoiceDatasetTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "audio").mkdir()
        (self.root / "labels").mkdir()
        (self.root / "consent.json").write_text(json.dumps({
            "voice_consent": "owner", "license": "self-owned", "singer_id": "test-singer"
        }))
        with wave.open(str(self.root / "audio" / "clip.wav"), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            wav.writeframes(b"\x00\x00" * 24000)
        (self.root / "labels" / "clip.json").write_text(json.dumps({
            "notes": [{"start": 0.0, "end": 0.5, "midi": 60, "phoneme": "a"}]
        }))
    def tearDown(self):
        self.tmp.cleanup()
    def test_valid_consent_and_alignment(self):
        manifest = inspect_dataset(self.root)
        self.assertEqual(len(manifest["clips"]), 1)
        self.assertEqual(manifest["clips"][0]["sample_rate"], 24000)
    def test_missing_consent_rejected(self):
        (self.root / "consent.json").unlink()
        with self.assertRaises(ValueError):
            inspect_dataset(self.root)
    def test_invalid_alignment_rejected(self):
        (self.root / "labels" / "clip.json").write_text(json.dumps({
            "notes": [{"start": 0.8, "end": 1.4, "midi": 60, "phoneme": "a"}]
        }))
        with self.assertRaises(ValueError):
            inspect_dataset(self.root)
    def test_unlicensed_voice_rejected(self):
        (self.root / "consent.json").write_text(json.dumps({
            "voice_consent": "unknown", "license": "none", "singer_id": "x"
        }))
        with self.assertRaises(ValueError):
            inspect_dataset(self.root)

if __name__ == "__main__":
    unittest.main()
