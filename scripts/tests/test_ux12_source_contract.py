"""UX12 source contracts; source-only checks never imply a real phone call."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[2] / "app/src"


class FinalUxSourceTest(unittest.TestCase):
    def test_history_detail_has_semantic_context_without_call_data(self):
        text = (BASE / "main/kotlin/pl/michalmatu/aicallbridge/conversation/ConversationHistoryActivity.kt").read_text()
        self.assertIn('"history-detail-heading"', text)
        self.assertIn("ConversationDirection.INCOMING", text)
        self.assertIn("DateFormat.getDateTimeInstance(", text)
        self.assertIn("detailBack.isEnabled = id != null", text)

    def test_screenshot_runner_accepts_unique_run_folder(self):
        text = (BASE / "androidTest/kotlin/pl/michalmatu/aicallbridge/AndroidVisualScreenAuditTest.kt").read_text()
        self.assertIn('getString("uiAuditRunId")', text)
        self.assertIn('"ui-audit-', text)
        self.assertIn('bitmap.compress(Bitmap.CompressFormat.JPEG', text)


if __name__ == "__main__":
    unittest.main()
