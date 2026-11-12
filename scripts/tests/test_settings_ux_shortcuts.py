"""Bottom navigation owns History, Lab and Settings; Home owns only unique actions."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class SettingsUxShortcutsContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (BASE / "MainActivity.kt").read_text()
        cls.nav = (BASE / "ui/AppNavigation.kt").read_text()
        cls.history = (BASE / "conversation/ConversationHistoryActivity.kt").read_text()
        cls.chat = (BASE / "conversation/ConversationChatPanel.kt").read_text()

    def test_history_has_one_persistent_primary_entry(self):
        self.assertIn('HISTORY(R.string.ui_tab_history', self.nav)
        self.assertIn('AppTab.HISTORY -> ConversationHistoryActivity::class.java', self.nav)
        self.assertNotIn('text = "Rozmowy i nagrania"', self.main)
        self.assertNotIn("addView(conversationHistoryButton)", self.main)
        self.assertNotIn("ConversationHistoryActivity::class.java", self.chat)
        self.assertNotIn("FLAG_SECURE", self.history)

    def test_offline_lab_settings_and_stop_are_not_duplicated(self):
        self.assertNotIn("AppTab.LAB", self.nav)
        developer = (BASE / "ui/DeveloperToolsActivity.kt").read_text()
        self.assertIn("ConversationSkillLabActivity::class.java", developer)
        self.assertIn("AppTab.SETTINGS -> SettingsHubActivity::class.java", self.nav)
        for tag in ('home-appointment-start', 'home-stop-ai'):
            self.assertEqual(1, self.main.count('tag = "' + tag + '"'))
        self.assertNotIn("ConversationSkillLabActivity::class.java", self.main)


if __name__ == "__main__":
    unittest.main()
