"""Static regression for owner-first navigation, without dialing or opening an audio stream."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class SettingsUxShortcutsContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (BASE / "MainActivity.kt").read_text()
        cls.history = (BASE / "conversation/ConversationHistoryActivity.kt").read_text()

    def test_history_has_a_direct_primary_entry(self):
        self.assertIn('text = "Rozmowy i nagrania"', self.main)
        self.assertIn("ConversationHistoryActivity::class.java", self.main)
        self.assertIn("addView(conversationHistoryButton)", self.main)
        self.assertNotIn("FLAG_SECURE", self.history)

    def test_offline_lab_and_stop_are_separate(self):
        self.assertIn('text = "Laboratorium offline"', self.main)
        self.assertIn("ConversationSkillLabActivity::class.java", self.main)
        self.assertIn("addView(labShortcutButton)", self.main)
        self.assertIn("addView(stopAiButton)", self.main)
        self.assertIn("addView(startAuthorizedTaskButton)", self.main)


if __name__ == "__main__":
    unittest.main()
