import unittest
from music_research_agent import SEEDS, analyze_record, research, fetch_metadata


class MusicResearchAgentTests(unittest.TestCase):
    def test_review_queue(self):
        def fake_fetch(url):
            return {"html_url": "https://github.com/example/project",
                    "default_branch": "main", "description": "Music model",
                    "license": {"spdx_id": "MIT"}}
        report = research(["ace_step"], fetcher=fake_fetch)
        self.assertEqual(report["status"], "RESEARCH_ONLY_NOT_TRAINED")
        item = report["sources"][0]
        self.assertEqual(item["source_license_spdx"], "MIT")
        self.assertEqual(item["weights_license"], "UNVERIFIED")
        self.assertTrue(item["review_required"])

    def test_fetch_failure_is_reported(self):
        def failing_fetch(url):
            raise OSError("offline")
        report = research(["audiocraft"], fetcher=failing_fetch)
        self.assertEqual(report["sources"][0]["status"], "FETCH_FAILED")

    def test_unknown_source_rejected(self):
        with self.assertRaises(ValueError):
            research(["untrusted"])

    def test_unapproved_api_host_rejected(self):
        with self.assertRaises(ValueError):
            fetch_metadata("https://example.org/repos/x/y")


if __name__ == "__main__":
    unittest.main()
