"""G5.3 real human review is never inferred by a validator."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
import wave

from scripts.aicall_tools.diagnostics.g53_private_audio_review import DIMENSIONS, render
from scripts.aicall_tools.diagnostics.g53_human_review_gate import (
    import_review, scan, validate_review,
)


class G53HumanReviewGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root.chmod(0o700)
        self.corpus = self.root / "session-synthetic"
        self.corpus.mkdir(mode=0o700)
        for name in ("turn-01-rx.wav", "turn-01-tx.wav"):
            with wave.open(str(self.corpus / name), "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(16000)
                w.writeframes(b"\0\0" * 8000)
        self.manifest = {
            "schema": "g53-synthetic-audio-review-v1",
            "source": "synthetic_offcall_test", "no_telecom": True,
            "model": "GEMMA_4_E2B",
            "sample_rate_hz": 16000, "pcm_format": "pcm_s16le_mono",
            "turns": [{"turn": 1, "reference_utterance": "próba",
                       "stt_final": "próba", "approved_response": "Odpowiedź",
                       "stt_ms": 100, "approval_ms": 200, "tts_end_ms": 300,
                       "rx_wav": "turn-01-rx.wav", "tx_wav": "turn-01-tx.wav"}],
        }
        (self.corpus / "manifest.json").write_text(json.dumps(self.manifest), encoding="utf-8")
        self.review = {
            "schema": "g53-human-review-v1", "source": "human",
            "reviewer_confirmed_listened": True,
            "manifest_sha256": hashlib.sha256(
                (self.corpus / "manifest.json").read_bytes()).hexdigest(),
            "reviews": [{"dimension": d, "verdict": "PASS",
                         "turn": 1, "note": "Ręczny dowód: tura pierwsza"}
                        for d in DIMENSIONS],
        }
        self.input = self.root / "review-input.json"

    def bytes(self) -> bytes:
        return json.dumps(self.review, ensure_ascii=False).encode("utf-8")

    def test_valid_private_import_is_atomic_and_never_overwrites(self) -> None:
        render(self.corpus)
        page = (self.corpus / "review.html").read_text(encoding="utf-8")
        self.assertIn(self.review["manifest_sha256"], page)
        self.assertIn("reviewer-confirmed", page)
        self.input.write_bytes(self.bytes())
        result = import_review(self.corpus, self.input)
        self.assertEqual("RECORDED_PASS_CANDIDATE", result["status"])
        stored = self.corpus / "human-review.json"
        self.assertEqual(self.input.read_bytes(), stored.read_bytes())
        self.assertEqual(0o600, stat.S_IMODE(stored.stat().st_mode))
        self.assertEqual(1, scan(self.root)["recorded"])
        with self.assertRaisesRegex(FileExistsError, "already_exists"):
            import_review(self.corpus, self.input)

    def test_unassessed_never_passes(self) -> None:
        self.review["reviews"][0]["verdict"] = "UNASSESSED"
        with self.assertRaisesRegex(ValueError, "unassessed_or_invalid"):
            validate_review(self.corpus, self.bytes())
        self.assertEqual(1, scan(self.root)["unassessed"])

    def test_wrong_session_and_missing_human_attestation_rejected(self) -> None:
        self.review["manifest_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "fingerprint_mismatch"):
            validate_review(self.corpus, self.bytes())
        self.review["manifest_sha256"] = hashlib.sha256(
            (self.corpus / "manifest.json").read_bytes()).hexdigest()
        self.review["reviewer_confirmed_listened"] = False
        with self.assertRaisesRegex(ValueError, "missing_review_attestation"):
            validate_review(self.corpus, self.bytes())

    def test_missing_duplicate_or_unknown_turn_rejected(self) -> None:
        self.review["reviews"][1]["dimension"] = DIMENSIONS[0]
        with self.assertRaisesRegex(ValueError, "invalid_or_duplicate_dimension"):
            validate_review(self.corpus, self.bytes())
        self.review["reviews"][1]["dimension"] = DIMENSIONS[1]
        self.review["reviews"][2]["turn"] = 999
        with self.assertRaisesRegex(ValueError, "unknown_review_evidence_turn"):
            validate_review(self.corpus, self.bytes())
        self.review["reviews"][2]["turn"] = 1
        self.review["reviews"][3]["note"] = "  "
        with self.assertRaisesRegex(ValueError, "missing_review_evidence_note"):
            validate_review(self.corpus, self.bytes())

    def test_negative_human_review_remains_recorded_fail(self) -> None:
        self.review["reviews"][0]["verdict"] = "FAIL"
        verdict = validate_review(self.corpus, self.bytes())
        self.assertEqual("RECORDED_FAIL", verdict["status"])
        self.assertEqual(1, verdict["failures"])

    def test_invalid_review_file_does_not_create_private_record(self) -> None:
        self.input.write_text('{"bad":true}')
        with self.assertRaisesRegex(ValueError, "missing_review_attestation"):
            import_review(self.corpus, self.input)
        self.assertFalse((self.corpus / "human-review.json").exists())

    def test_review_symlink_is_rejected(self) -> None:
        self.input.write_bytes(self.bytes())
        link = self.root / "link.json"
        link.symlink_to(self.input)
        with self.assertRaisesRegex(ValueError, "invalid_review_input_file"):
            import_review(self.corpus, link)


if __name__ == "__main__":
    unittest.main()
