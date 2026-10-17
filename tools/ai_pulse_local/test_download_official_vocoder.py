import tempfile
import unittest
import zipfile
from pathlib import Path
from download_official_vocoder import safe_extract, download


class VocoderDownloaderTests(unittest.TestCase):
    def test_reject_archive_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "unsafe.oudep"
            with zipfile.ZipFile(archive, "w") as z:
                z.writestr("../escape.txt", "bad")
            with self.assertRaises(ValueError):
                safe_extract(archive, root / "out")
            self.assertFalse((root / "escape.txt").exists())

    def test_extract_valid_oudep(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "valid.oudep"
            with zipfile.ZipFile(archive, "w") as z:
                z.writestr("oudep.yaml", "id: test-vocoder\nversion: '1.0'\n")
            safe_extract(archive, root / "out")
            self.assertTrue((root / "out" / "oudep.yaml").is_file())

    def test_reject_unofficial_url(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                download({"url": "https://example.com/unsafe.oudep",
                          "name": "unsafe.oudep", "size": 1}, Path(tmp))


if __name__ == "__main__":
    unittest.main()
