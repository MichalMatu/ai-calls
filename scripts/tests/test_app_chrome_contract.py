"""All product screens use one navigation shell without runtime changes."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

class ChromeSourceContract(unittest.TestCase):
    def test_one_shell_on_product_screens(self):
        targets = [["conversation/ScenarioLibraryActivity.kt","AGENT"],["conversation/ConversationHistoryActivity.kt","HISTORY"],["conversation/ConversationSkillLabActivity.kt","LAB"],["conversation/ConversationSkillsActivity.kt","AGENT"],["conversation/ConversationPreparationActivity.kt","AGENT"],["audioengines/AudioEngineSettingsActivity.kt","MODELS"],["textagent/LanguageModelActivity.kt","MODELS"],["incoming/IncomingAssistantSettingsActivity.kt","SETTINGS"],["tools/ConversationToolsActivity.kt","AGENT"],["debug/UsbDebugPanelActivity.kt","SETTINGS"],["identity/PhoneEnrollmentActivity.kt","AGENT"],["campaign/CampaignToolActivity.kt","HOME"]]
        for relative, tab in targets:
            with self.subTest(screen=relative):
                code = (ROOT / relative).read_text()
                self.assertIn("UiChromeActivity(AppTab." + tab + ")", code)
        chrome = (ROOT / "ui/UiChromeActivity.kt").read_text()
        nav = (ROOT / "ui/AppNavigation.kt").read_text()
        self.assertIn("super.setContentView(AppNavigation.shell(this, view, activeTab))", chrome)
        self.assertIn("app-bottom-navigation", nav)
        self.assertIn("WindowInsets.Type.systemBars()", nav)

    def test_screenshots_remain_allowed(self):
        for path in ("conversation/ConversationHistoryActivity.kt",
                     "conversation/ConversationSkillsActivity.kt",
                     "conversation/ConversationPreparationActivity.kt",
                     "incoming/IncomingAssistantSettingsActivity.kt"):
            self.assertNotIn("FLAG_SECURE", (ROOT / path).read_text())

if __name__ == "__main__":
    unittest.main()
