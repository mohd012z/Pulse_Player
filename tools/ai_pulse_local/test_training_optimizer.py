import unittest
from training_optimizer import plan_training


class TrainingOptimizerTests(unittest.TestCase):
    def test_balanced_profile(self):
        result = plan_training({"rights_confirmed": True,
                                "voice_consent_confirmed": True,
                                "audio_count": 20})
        self.assertEqual(result["rank"], 64)
        self.assertEqual(result["effective_batch_size"], 4)
        self.assertEqual(result["status"], "PLAN_ONLY_NOT_TRAINED")

    def test_reject_missing_rights(self):
        with self.assertRaises(ValueError):
            plan_training({"audio_count": 20, "rights_confirmed": False,
                           "voice_consent_confirmed": True})

    def test_low_vram_profile(self):
        result = plan_training({"audio_count": 6, "profile": "low_vram",
                                "rights_confirmed": True,
                                "voice_consent_confirmed": True})
        self.assertEqual(result["rank"], 16)
        self.assertEqual(result["suggested_epochs"], 200)


if __name__ == "__main__":
    unittest.main()
