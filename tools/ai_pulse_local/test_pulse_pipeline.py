import unittest
from pulse_pipeline import validate_phrase


class PipelineTests(unittest.TestCase):
    def test_valid_phrase(self):
        data = {"phonemes": [1, 2], "durations": [0.2, 0.4], "f0": [220, 220]}
        self.assertEqual(validate_phrase(data), data)

    def test_reject_mismatched_phrase(self):
        with self.assertRaises(ValueError):
            validate_phrase({"phonemes": [1, 2], "durations": [0.2], "f0": [220]})

    def test_reject_nonfinite_pitch(self):
        with self.assertRaises(ValueError):
            validate_phrase({"phonemes": [1], "durations": [0.2], "f0": [float("nan")]})


if __name__ == "__main__":
    unittest.main()
