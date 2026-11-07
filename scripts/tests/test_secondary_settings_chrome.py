"""Both extra settings screens use the same single non-nested chrome as all child screens."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge/ui"
class SecondarySettingsChromeContract(unittest.TestCase):
    def test_general_and_developer_chrome(self):
        for name in ("GeneralSettingsActivity","DeveloperToolsActivity"):
            with self.subTest(name=name):
                code=(ROOT/(name+".kt")).read_text()
                self.assertIn("class "+name+" : UiChromeActivity(AppTab.SETTINGS)",code)
                self.assertIn("setContentView(ScrollView(this).apply",code)
                self.assertNotIn("AppNavigation.install(this,",code)
if __name__=="__main__": unittest.main()
