import json
import math
import struct
import tempfile
import unittest
import wave
from pathlib import Path
from model_comparison_agent import compare


def wav(path, amplitude):
    samples = [int(amplitude * math.sin(2 * math.pi * 440 * i / 16000))
               for i in range(4000)]
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(struct.pack("<" + "h" * len(samples), *samples))


class ComparisonTests(unittest.TestCase):
    def test_pair_comparison_is_not_claimed_as_quality_win(self):
        with tempfile.TemporaryDirectory() as temp:
            a, b = Path(temp) / "a.wav", Path(temp) / "b.wav"
            wav(a, 9000)
            wav(b, 12000)
            report = compare({"pairs": [{"prompt_id": "p1", "seed": 42,
                                          "baseline_wav": str(a),
                                          "candidate_wav": str(b)}]})
            self.assertEqual(report["pairs_evaluated"], 1)
            self.assertFalse(report["model_improvement_proven"])
            self.assertEqual(report["results"][0]["perceptual_winner"], "UNDETERMINED")

    def test_empty_manifest_rejected(self):
        with self.assertRaises(ValueError):
            compare({"pairs": []})


if __name__ == "__main__":
    unittest.main()
