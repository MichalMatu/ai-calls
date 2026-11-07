"""UI language selection: PL/EN/auto, separate from speech and persisted across screens."""
import xml.etree.ElementTree as ET
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
K=ROOT/"kotlin/pl/michalmatu/aicallbridge"
NS="{http://schemas.android.com/apk/res/android}"
class LocaleChoiceContract(unittest.TestCase):
    def test_manifest_lists_supported_languages(self):
        app=ET.parse(ROOT/"AndroidManifest.xml").getroot().find("application")
        self.assertEqual("@xml/locale_config",app.attrib[NS+"localeConfig"])
        cfg=ET.parse(ROOT/"res/xml/locale_config.xml").getroot()
        self.assertEqual({"pl","en"},{n.attrib[NS+"name"] for n in cfg.findall("locale")})
    def test_language_is_ui_only_and_available(self):
        locale=(K/"ui/AppLanguage.kt").read_text()
        hub=(K/"ui/SettingsHubActivity.kt").read_text()
        self.assertIn("LocaleManager::class.java",locale)
        self.assertIn("LocaleList.forLanguageTags",locale)
        self.assertIn("AppLanguage.wrap(newBase)",(K/"ui/AppBaseActivity.kt").read_text())
        for tag in ("auto","pl","en"):
            self.assertIn('"' + tag + '"',hub)
        self.assertNotIn("CallRuntimePreferences",locale)
        self.assertNotIn("SpeechInputId",locale)
if __name__=="__main__":unittest.main()
