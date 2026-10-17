import tempfile
import unittest
from pathlib import Path
from voicebank_preflight import inspect


class VoicebankPreflightTests(unittest.TestCase):
    def test_structural_voicebank(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "dsconfig.yaml").write_text("acoustic: acoustic.onnx\nphonemes: phonemes.txt\nvocoder: nsf_hifigan\n")
            (root / "acoustic.onnx").write_bytes(b"fixture-not-real-onnx")
            (root / "phonemes.txt").write_text("SP\na\nb\n")
            result = inspect(root)
            self.assertEqual(result["phoneme_count"], 3)
            self.assertTrue(result["requires_external_vocoder"])
            self.assertEqual(result["status"], "STRUCTURAL_ONLY_NOT_INFERENCE_PROVEN")

    def test_reject_path_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "dsconfig.yaml").write_text("acoustic: ../bad.onnx\nvocoder: v\n")
            with self.assertRaises(ValueError):
                inspect(root)

    def test_reject_duplicate_phonemes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "dsconfig.yaml").write_text("acoustic: acoustic.onnx\nvocoder: v\n")
            (root / "acoustic.onnx").write_bytes(b"fixture")
            (root / "phonemes.txt").write_text("a\na\n")
            with self.assertRaises(ValueError):
                inspect(root)


if __name__ == "__main__":
    unittest.main()
