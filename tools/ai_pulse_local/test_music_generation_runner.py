import tempfile
import unittest
import wave
from pathlib import Path
from music_generation_runner import inspect_wav


class MusicRunnerTests(unittest.TestCase):
    def test_valid_wav(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "song.wav"
            with wave.open(str(path), "wb") as audio:
                audio.setnchannels(1)
                audio.setsampwidth(2)
                audio.setframerate(24000)
                audio.writeframes((1000).to_bytes(2, "little", signed=True) * 4800)
            evidence = inspect_wav(path)
            self.assertEqual(evidence["sample_rate"], 24000)
            self.assertAlmostEqual(evidence["duration_seconds"], 0.2)
            self.assertEqual(evidence["peak_pcm16"], 1000)

    def test_silent_wav_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "silent.wav"
            with wave.open(str(path), "wb") as audio:
                audio.setnchannels(1)
                audio.setsampwidth(2)
                audio.setframerate(24000)
                audio.writeframes(bytes(9600))
            with self.assertRaises(ValueError):
                inspect_wav(path)


if __name__ == "__main__":
    unittest.main()
