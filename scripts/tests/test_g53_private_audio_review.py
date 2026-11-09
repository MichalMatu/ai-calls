"""G5.3 private synthetic corpus review: never grade a model automatically."""
from __future__ import annotations

import json
from pathlib import Path
import stat
import tempfile
import unittest
import wave

from scripts.aicall_tools.diagnostics.g53_private_audio_review import render, load


class G53PrivateAudioReviewTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root.chmod(0o700)
        self.wav = "turn-01-rx.wav"
        for filename in ("turn-01-rx.wav", "turn-01-tx.wav"):
            with wave.open(str(self.root / filename), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(16000)
                output.writeframes(b"\0\0" * 8000)
        self.manifest = {
            "schema": "g53-synthetic-audio-review-v1",
            "source": "synthetic_offcall_test",
            "no_telecom": True,
            "model": "GEMMA_4_E2B",
            "stt_provider": "ANDROID_STT",
            "tts_provider": "ANDROID_TTS_PL",
            "sample_rate_hz": 16000,
            "pcm_format": "pcm_s16le_mono",
            "turns": [{
                "turn": 1, "reference_utterance": "<script>alert(1)</script>",
                "stt_final": "fikcyjny użytkownik",
                "approved_response": "Witaj & do zobaczenia",
                "stt_ms": 300, "approval_ms": 1300, "tts_end_ms": 1500,
                "rx_wav": "turn-01-rx.wav", "tx_wav": "turn-01-tx.wav",
            }],
        }
        self.write()

    def write(self) -> None:
        (self.root / "manifest.json").write_text(
            json.dumps(self.manifest), encoding="utf-8")

    def test_local_review_is_private_and_human_unassessed(self) -> None:
        result = render(self.root)
        self.assertEqual({"turns": 1, "wav_files": 2, "dimensions": 8,
                          "review_status": "UNASSESSED"}, result)
        output = self.root / "review.html"
        self.assertEqual(0o600, stat.S_IMODE(output.stat().st_mode))
        html = output.read_text(encoding="utf-8")
        self.assertIn("&lt;script&gt;alert(1)&lt;/script&gt;", html)
        self.assertNotIn("<script>alert(1)</script>", html)
        self.assertIn("UNASSESSED", html)
        self.assertIn("turn-01-tx.wav", html)
        self.assertIn("connect-src &#39;none&#39;", html)

    def test_traversal_is_rejected(self) -> None:
        self.manifest["turns"][0]["tx_wav"] = "../outside.wav"
        self.write()
        with self.assertRaisesRegex(ValueError, "invalid_or_duplicate_wav_name"):
            load(self.root)

    def test_non_pcm_wave_is_rejected(self) -> None:
        with wave.open(str(self.root / "turn-01-rx.wav"), "wb") as output:
            output.setnchannels(2)
            output.setsampwidth(2)
            output.setframerate(44100)
            output.writeframes(b"\0" * 1000)
        with self.assertRaisesRegex(ValueError, "unexpected_pcm_format"):
            render(self.root)

    def test_incomplete_manifest_or_turn_is_not_review_ready(self) -> None:
        self.manifest["turns"][0]["stt_final"] = ""
        self.write()
        with self.assertRaisesRegex(ValueError, "missing_review_text"):
            render(self.root)


if __name__ == "__main__":
    unittest.main()
