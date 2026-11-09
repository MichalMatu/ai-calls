"""G5.3 private synthetic corpus review: never grade a model automatically."""
from __future__ import annotations

import json
from pathlib import Path
import stat
import tempfile
import unittest
import wave

from scripts.aicall_tools.diagnostics.g53_private_audio_review import render, load, render_all


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

    def test_regeneration_is_atomic_private_and_updates_old_html(self) -> None:
        # Simulate HTML generated before the SHA-linked review gate existed.
        old = self.root / "review.html"
        old.write_text("legacy review page", encoding="utf-8")
        old.chmod(0o600)
        manifest_before = (self.root / "manifest.json").read_bytes()
        wav_before = {p.name: p.read_bytes() for p in self.root.glob("*.wav")}
        self.assertEqual("UNASSESSED", render(self.root)["review_status"])
        html = old.read_text(encoding="utf-8")
        self.assertIn("const MANIFEST_SHA256", html)
        self.assertIn("reviewer-confirmed", html)
        self.assertNotIn("legacy review page", html)
        self.assertEqual(0o600, stat.S_IMODE(old.stat().st_mode))
        self.assertEqual(manifest_before, (self.root / "manifest.json").read_bytes())
        self.assertEqual(wav_before, {p.name: p.read_bytes() for p in self.root.glob("*.wav")})
        self.assertFalse(list(self.root.glob(".review-*.tmp")))

    def test_root_refresh_preserves_existing_human_review(self) -> None:
        session = self.root / "session-test"
        session.mkdir(mode=0o700)
        for filename in ("turn-01-rx.wav", "turn-01-tx.wav"):
            (session / filename).write_bytes((self.root / filename).read_bytes())
        (session / "manifest.json").write_text(json.dumps(self.manifest), encoding="utf-8")
        completed = self.root / "session-done"
        completed.mkdir(mode=0o700)
        (completed / "human-review.json").write_text("human evidence is private", encoding="utf-8")
        result = render_all(self.root)
        self.assertEqual({"sessions": 2, "refreshed": 1, "retained_reviewed": 1}, result)
        self.assertIn("const MANIFEST_SHA256", (session / "review.html").read_text(encoding="utf-8"))
        self.assertEqual("human evidence is private", (completed / "human-review.json").read_text())
        self.assertFalse((completed / "review.html").exists())

    def test_render_rejects_existing_review_symlink_and_completed_review(self) -> None:
        target = self.root / "outside.html"
        target.write_text("unrelated")
        (self.root / "review.html").symlink_to(target)
        with self.assertRaisesRegex(ValueError, "unsafe_existing_private_review_html"):
            render(self.root)
        self.assertEqual("unrelated", target.read_text())
        (self.root / "review.html").unlink()
        (self.root / "human-review.json").write_text("recorded")
        with self.assertRaisesRegex(ValueError, "completed_human_review"):
            render(self.root)

    def test_root_refresh_rejects_symlinked_session(self) -> None:
        (self.root / "session-symlink").symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "invalid_private_session_path"):
            render_all(self.root)

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
