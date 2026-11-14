import tempfile
import unittest
import wave
from pathlib import Path

from aicall_tools.diagnostics.gemma_audio_fixture_lab import generate, pcm16, SAMPLE_RATE


class GemmaAudioFixtureLabTest(unittest.TestCase):
    def test_fixtures_are_deterministic_valid_wav(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            first = generate(Path(a))
            second = generate(Path(b))
            self.assertEqual(first, second)
            self.assertEqual(4, len(first["cases"]))
            for item in first["cases"]:
                with wave.open(str(Path(a) / (item["name"] + ".wav")), "rb") as source:
                    self.assertEqual((1, 2, SAMPLE_RATE), (
                        source.getnchannels(), source.getsampwidth(), source.getframerate()))
                    self.assertEqual(item["frames"], source.getnframes())
                    self.assertEqual(pcm16(item["name"], item["seconds"]), source.readframes(item["frames"]))

    def test_max_audio_window_and_invalid_ranges(self):
        self.assertEqual(32_000, len(pcm16("silence")))
        with self.assertRaises(ValueError):
            pcm16("silence", 0)
        with self.assertRaises(ValueError):
            pcm16("silence", 31)
        with self.assertRaises(ValueError):
            pcm16("unrecognized", 1)


if __name__ == "__main__":
    unittest.main()
