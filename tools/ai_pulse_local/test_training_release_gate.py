import hashlib
import tempfile
import unittest
from pathlib import Path
from training_release_gate import REQUIRED, evaluate


class ReleaseGateTests(unittest.TestCase):
    def test_missing_evidence_blocks_release(self):
        with tempfile.TemporaryDirectory() as temp:
            report = evaluate(temp, {})
            self.assertEqual(report["status"], "BLOCKED_MISSING_EVIDENCE")
            self.assertFalse(report["ready_for_release"])

    def test_hash_valid_evidence_requires_manual_review(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            proof = root / "proof.txt"
            proof.write_text("test evidence")
            digest = hashlib.sha256(proof.read_bytes()).hexdigest()
            manifest = {name: {"path": "proof.txt", "sha256": digest} for name in REQUIRED}
            report = evaluate(root, manifest)
            self.assertEqual(report["status"], "EVIDENCE_FILES_VERIFIED_MANUAL_REVIEW_REQUIRED")
            self.assertFalse(report["ready_for_release"])

    def test_wrong_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "proof.txt").write_text("test evidence")
            report = evaluate(root, {"generated_audio": {"path": "proof.txt", "sha256": "0" * 64}})
            self.assertFalse(report["checks"]["generated_audio"]["passed"])


if __name__ == "__main__":
    unittest.main()
