import math
import struct
import tempfile
import unittest
import wave
from pathlib import Path
from music_evaluation_agent import evaluate_wav


def make_wav(path, amplitude):
    rate = 16000
    samples = [int(amplitude * math.sin(2 * math.pi * 440 * i / rate))
               for i in range(rate // 4)]
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        wav.writeframes(struct.pack("<" + "h" * len(samples), *samples))


class EvaluationAgentTests(unittest.TestCase):
    def test_valid_pcm_wav(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "test.wav"
            make_wav(path, 12000)
            report = evaluate_wav(path)
            self.assertEqual(report["status"], "TECHNICAL_WAV_CHECK_PASSED")
            self.assertFalse(report["perceptual_quality_verified"])

    def test_silent_wav_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "silence.wav"
            make_wav(path, 0)
            report = evaluate_wav(path)
            self.assertIn("SILENT_OR_NEAR_SILENT", report["issues"])


if __name__ == "__main__":
    unittest.main()
