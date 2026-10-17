import tempfile
import unittest
from pathlib import Path
from pytorch_musicgen import render


class MusicGenGateTests(unittest.TestCase):
    def test_license_required_before_import(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                render("Original instrumental", Path(temp) / "out.wav")

    def test_allowlist_enforced(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                render("Original instrumental", Path(temp) / "out.wav",
                       model_name="unknown/untrusted", license_ack=True)

    def test_duration_bounded(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                render("Original instrumental", Path(temp) / "out.wav",
                       seconds=500, license_ack=True)


if __name__ == "__main__":
    unittest.main()
