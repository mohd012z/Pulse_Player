import unittest
from music_provider_guardrails import build_plan


class ProviderGuardrailTests(unittest.TestCase):
    def request(self, **changes):
        value = {"backend": "ace_step", "prompt": "Original upbeat synth pop",
                 "duration_seconds": 45, "license_reviewed": True}
        value.update(changes)
        return value

    def test_valid_plan(self):
        result = build_plan(self.request())
        self.assertEqual(result["status"], "PLANNED_NOT_GENERATED")
        self.assertTrue(result["requires_installed_backend"])

    def test_unreviewed_license(self):
        with self.assertRaises(ValueError):
            build_plan(self.request(license_reviewed=False))

    def test_unlicensed_reference_audio(self):
        with self.assertRaises(ValueError):
            build_plan(self.request(reference_audio="voice.wav"))

    def test_noncommercial_musicgen(self):
        with self.assertRaises(ValueError):
            build_plan(self.request(backend="musicgen", distribution="commercial"))

    def test_reject_singer_impersonation(self):
        with self.assertRaises(ValueError):
            build_plan(self.request(prompt="Clone the voice of a famous singer"))


if __name__ == "__main__":
    unittest.main()
