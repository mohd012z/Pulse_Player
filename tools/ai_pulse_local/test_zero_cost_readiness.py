import tempfile
import unittest
from pathlib import Path
from zero_cost_readiness import assess

class ZeroCostReadinessTests(unittest.TestCase):
    def test_missing_model_is_reported_without_claiming_inference(self):
        with tempfile.TemporaryDirectory() as temp:
            report = assess(model_root=str(Path(temp) / "missing"), disk_path=temp)
            self.assertEqual(report["mode"], "ZERO_PAID_SERVICES")
            self.assertFalse(report["neural_inference_proven"])
            self.assertIn("NO_LOCAL_SAFETENSORS_CHECKPOINT", report["blockers"])

    def test_local_checkpoint_is_only_a_candidate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "weights.safetensors").write_bytes(b"not a real model")
            report = assess(model_root=temp, disk_path=temp)
            self.assertEqual(report["checkpoint_candidates"], ["weights.safetensors"])
            self.assertFalse(report["neural_inference_proven"])

if __name__ == "__main__":
    unittest.main()
