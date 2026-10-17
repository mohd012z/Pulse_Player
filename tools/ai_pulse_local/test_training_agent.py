import tempfile
import unittest
from pathlib import Path
from training_agent import make_plan, run


class TrainingAgentTests(unittest.TestCase):
    def fixture(self, root):
        root = Path(root)
        source = root / "sidestep"
        source.mkdir()
        (source / "train.py").write_text("# fixture", encoding="utf-8")
        weights = root / "weights"
        weights.mkdir()
        data = root / "data"
        data.mkdir()
        (data / "sample.txt").write_text("fixture", encoding="utf-8")
        return {
            "sidestep_root": str(source), "checkpoint_dir": str(weights),
            "dataset_dir": str(data), "output_dir": str(root / "output"),
            "rights_confirmed": True, "consent_confirmed": True,
        }

    def test_plan_is_nonexecuting(self):
        with tempfile.TemporaryDirectory() as temp:
            spec = self.fixture(temp)
            result = run(spec)
            self.assertEqual(result["status"], "AWAITING_OPERATOR_APPROVAL")
            self.assertTrue((Path(spec["output_dir"]) / "ai_pulse_agent_plan.json").is_file())
            self.assertFalse((Path(spec["output_dir"]) / "ai_pulse_training.log").exists())

    def test_execute_requires_approval(self):
        with tempfile.TemporaryDirectory() as temp:
            spec = self.fixture(temp)
            with self.assertRaises(PermissionError):
                run(spec, execute=True)

    def test_reject_missing_data_rights(self):
        with tempfile.TemporaryDirectory() as temp:
            spec = self.fixture(temp)
            spec["rights_confirmed"] = False
            with self.assertRaises(ValueError):
                make_plan(spec)


if __name__ == "__main__":
    unittest.main()
