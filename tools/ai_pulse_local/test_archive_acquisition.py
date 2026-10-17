import io
import tempfile
import unittest
import zipfile
from pathlib import Path
from archive_acquisition import safe_extract, urls


class ArchiveAcquisitionTests(unittest.TestCase):
    def test_official_pinned_urls(self):
        links = urls("ace_step", "a" * 40)
        self.assertTrue(links[0].startswith("https://codeload.github.com/"))
        self.assertTrue(all("a" * 40 in url for url in links))

    def test_reject_mutable_ref(self):
        with self.assertRaises(ValueError):
            urls("ace_step", "main")

    def test_extract_source_zip(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "source.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("project-commit/README.md", "licensed source")
            safe_extract(archive, root / "out")
            self.assertEqual((root / "out" / "README.md").read_text(), "licensed source")

    def test_reject_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "bad.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("project/../../escape.txt", "unsafe")
            with self.assertRaises(ValueError):
                safe_extract(archive, root / "out")


if __name__ == "__main__":
    unittest.main()
