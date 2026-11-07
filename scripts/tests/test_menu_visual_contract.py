"""Direct navigation and safe actions; no one-button home accordions."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

class MenuVisualSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (ROOT / "MainActivity.kt").read_text()
        cls.nav = (ROOT / "ui/AppNavigation.kt").read_text()
        cls.hub = (ROOT / "ui/SettingsHubActivity.kt").read_text()
        cls.general = (ROOT / "ui/GeneralSettingsActivity.kt").read_text()
        cls.controller = (ROOT / "ui/GeneralSettingsController.kt").read_text()
        cls.chat = (ROOT / "conversation/ConversationChatPanel.kt").read_text()

    def test_safe_home_routes(self):
        for x in ("addView(startAuthorizedTaskButton)", "addView(stopAiButton)",
                  "addView(conversationHistoryButton)", "addView(labShortcutButton)",
                  "addView(oneTapButton)", "AppNavigation.bar(this@MainActivity, AppTab.HOME)"):
            self.assertIn(x, self.main)
        self.assertNotIn("UiAccordionSection(", self.main)
        self.assertIn("MainActivityDeveloperProbes", self.main)

    def test_normal_menu_and_settings_are_not_fake_accordions(self):
        for x in ("AppTab.HOME", "AppTab.HISTORY", "AppTab.LAB", "AppTab.SETTINGS",
                  "app-bottom-navigation", "app-tab:"):
            self.assertIn(x, self.nav)
        for x in ("SettingsHubActivity", "GeneralSettingsActivity", "DeveloperToolsActivity",
                  "AudioEngineSettingsActivity", "IncomingAssistantSettingsActivity"):
            self.assertIn(x, self.hub)
        for x in ("settings-audio-mode-spinner", "settings-provider-spinner",
                  "controller.selectMode(selected)", "controller.selectTextModel(selected)"):
            self.assertIn(x, self.general)
        for x in ("CallRuntimePreferences", "saveAudioMode", "saveTextLlmProvider"):
            self.assertIn(x, self.controller)
        self.assertNotIn("CallRuntimePreferences(", self.general)

    def test_safe_areas_and_chat(self):
        self.assertIn("WindowInsets.Type.systemBars()", self.main)
        self.assertIn("WindowInsets.Type.systemBars()", self.nav)
        for x in ("live-chat-scroll", "live-chat-toggle", "clearButton.visibility"):
            self.assertIn(x, self.chat)

if __name__ == "__main__": unittest.main()
