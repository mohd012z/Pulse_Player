import json
import struct
import tempfile
import unittest
from pathlib import Path
from checkpoint_evidence import verify_checkpoints


class CheckpointEvidenceTests(unittest.TestCase):
    def test_missing_checkpoint_is_not_proof(self):
        with tempfile.TemporaryDirectory() as temp:
            report = verify_checkpoints(temp)
            self.assertEqual(report["valid_checkpoints"], 0)

    def test_valid_safetensors_checkpoint(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            header = json.dumps({"weight": {"dtype": "F32", "shape": [2],
                                            "data_offsets": [0, 8]}}).encode("utf-8")
            (root / "adapter.safetensors").write_bytes(
                struct.pack("<Q", len(header)) + header + bytes(8))
            report = verify_checkpoints(root)
            self.assertEqual(report["valid_checkpoints"], 1)
            self.assertFalse(report["inference_verified"])

    def test_corrupt_checkpoint_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            (Path(temp) / "broken.safetensors").write_bytes(b"bad")
            report = verify_checkpoints(temp)
            self.assertEqual(report["valid_checkpoints"], 0)


if __name__ == "__main__":
    unittest.main()
