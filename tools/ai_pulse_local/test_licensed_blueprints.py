import tempfile
import unittest
from pathlib import Path
from licensed_blueprints import SOURCES, checkout


class LicensedBlueprintTests(unittest.TestCase):
    def test_official_source_registry(self):
        self.assertIn("ace_step", SOURCES)
        self.assertIn("diffsinger", SOURCES)
        self.assertTrue(all(x["url"].startswith("https://github.com/") for x in SOURCES.values()))

    def test_reject_unknown_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                checkout("unknown", Path(tmp) / "repo", acknowledge=True)

    def test_require_license_acknowledgement(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                checkout("ace_step", Path(tmp) / "repo")

    def test_reject_unpinned_short_sha(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                checkout("ace_step", Path(tmp) / "repo", acknowledge=True, revision="abc")


if __name__ == "__main__":
    unittest.main()
