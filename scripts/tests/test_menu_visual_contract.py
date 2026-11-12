"""Consumer navigation exposes one model editor and no legacy runtime selectors."""
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class MenuVisualSourceContract(unittest.TestCase):
    def test_home_keeps_task_and_stop_without_dialing_on_navigation(self):
        source=(ROOT/"MainActivity.kt").read_text()
        self.assertIn('tag = "home-task-start"',source)
        self.assertIn('tag = "home-stop-ai"',source)
        self.assertIn("PhoneTaskWizardActivity::class.java",source)
        self.assertNotIn("placeCall(",source)
    def test_settings_offer_one_editor_and_real_permissions(self):
        source=(ROOT/"ui/SettingsHubActivity.kt").read_text()
        destinations=re.findall(r"destination\([^\n]+",source)
        self.assertEqual(sum("LanguageModelActivity::class.java" in x for x in destinations),1)
        self.assertFalse(any("AudioEngineSettingsActivity::class.java" in x for x in destinations))
        self.assertIn("GeneralSettingsActivity::class.java",source)
        permissions=(ROOT/"ui/GeneralSettingsActivity.kt").read_text()
        self.assertIn("DialerRoleCard(this)",permissions)
        self.assertNotIn("TextLlmProvider",permissions)
        self.assertNotIn("CallAudioMode",permissions)
    def test_four_primary_tabs_and_system_insets_are_preserved(self):
        source=(ROOT/"ui/AppNavigation.kt").read_text()
        tabs=source[source.index("enum class AppTab"):source.index("internal object AppNavigation")]
        names=re.findall(r"^    ([A-Z_]+)\(R.string",tabs,re.M)
        self.assertEqual(names,["HOME","HISTORY","AGENT","SETTINGS"])
        self.assertIn("WindowInsets.Type.systemBars()",source)
if __name__=="__main__": unittest.main()
