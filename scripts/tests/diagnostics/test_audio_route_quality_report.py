"""No transcripts or live phone data in the audio-route quality report."""

import json
import tempfile
import unittest
from pathlib import Path

from aicall_tools.diagnostics.audio_route_quality_report import add, read_ledger, summary, validate


class AudioRouteQualityReportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.ledger = Path(self.temp.name) / "quality.jsonl"
        self.sample = {
            "run_id": "s22-gemma-android-synthetic-001",
            "source_sha": "9c0d987001b6e02586e3f944853a180c87ecd08d",
            "input": "GEMMA_AUDIO_DIRECT",
            "model": "GEMMA_4_E2B",
            "tts_engine": "ANDROID_TTS",
            "mode": "synthetic_offcall",
            "cases": 3,
            "passed": 3,
            "wer_errors": 1,
            "reference_words": 16,
            "end_to_end_ms": 9700,
            "stop_ok": True,
            "barge_in_ok": None,
            "end_call_ok": None,
        }

    def test_empty_report_is_unmeasured_not_failed(self):
        report = summary([])
        self.assertIn("nieprzetestowane", report)
        self.assertIn("GEMMA_AUDIO_DIRECT", report)
        self.assertNotIn("100.0%", report)

    def test_append_and_read(self):
        add(self.ledger, self.sample)
        rows = read_ledger(self.ledger)
        self.assertEqual(rows, [self.sample])
        report = summary(rows)
        self.assertIn("3/3", report)
        self.assertIn("6.2%", report)
        self.assertIn("9700/9700 ms", report)

    def test_duplicate_run_is_not_replayed(self):
        add(self.ledger, self.sample)
        with self.assertRaisesRegex(ValueError, "duplicate_run_id"):
            add(self.ledger, self.sample)
        self.assertEqual(len(read_ledger(self.ledger)), 1)

    def test_reject_private_audio_or_transcript_fields(self):
        with self.assertRaisesRegex(ValueError, "unexpected_fields:transcript"):
            validate({**self.sample, "transcript": "private speech"})
        with self.assertRaisesRegex(ValueError, "unexpected_fields:phone"):
            validate({**self.sample, "phone": "123"})

    def test_reject_unsupported_route_and_bad_metrics(self):
        with self.assertRaisesRegex(ValueError, "unsupported_gemma_audio_model"):
            validate({**self.sample, "model": "USER_LITERT"})
        with self.assertRaisesRegex(ValueError, "invalid_end_to_end_ms"):
            validate({**self.sample, "end_to_end_ms": -10})
        with self.assertRaisesRegex(ValueError, "invalid_case_counts"):
            validate({**self.sample, "passed": 4})
        with self.assertRaisesRegex(ValueError, "wer_fields_must_be_paired"):
            validate({k: v for k, v in self.sample.items() if k != "reference_words"})

    def test_corrupt_line_fails_closed(self):
        self.ledger.write_text(json.dumps(self.sample) + "\n{\"bad\":true}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "invalid_ledger_line_2"):
            read_ledger(self.ledger)


if __name__ == "__main__":
    unittest.main()
