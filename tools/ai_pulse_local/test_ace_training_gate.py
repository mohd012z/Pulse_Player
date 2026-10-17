import tempfile
import unittest
from pathlib import Path
from ace_training_gate import prepare


class AceTrainingGateTests(unittest.TestCase):
    def setup_case(self, tmp):
        root = Path(tmp)
        sidestep = root / "sidestep"
        sidestep.mkdir()
        (sidestep / "train.py").write_text("# test fixture")
        checkpoints = root / "checkpoints"
        checkpoints.mkdir()
        dataset = root / "dataset"
        dataset.mkdir()
        (dataset / "sample.txt").write_text("preprocessed fixture")
        return {"sidestep_root": str(sidestep),
                "checkpoint_dir": str(checkpoints),
                "dataset_dir": str(dataset),
                "output_dir": str(root / "output"),
                "rights_confirmed": True,
                "consent_confirmed": True}

    def test_preflight_does_not_train(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = prepare(self.setup_case(tmp))
            self.assertEqual(result["status"], "READY_TO_TRAIN_NOT_STARTED")
            self.assertFalse(result["evidence"]["training_executed"])
            self.assertIn("fixed", result["command"])

    def test_reject_missing_consent(self):
        with tempfile.TemporaryDirectory() as tmp:
            spec = self.setup_case(tmp)
            spec["consent_confirmed"] = False
            with self.assertRaises(ValueError):
                prepare(spec)

    def test_reject_invalid_epoch(self):
        with tempfile.TemporaryDirectory() as tmp:
            spec = self.setup_case(tmp)
            spec["epochs"] = 0
            with self.assertRaises(ValueError):
                prepare(spec)


if __name__ == "__main__":
    unittest.main()
