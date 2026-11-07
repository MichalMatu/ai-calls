"""Global platform UI theme is shared by all Activities without changing runtime code."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
NS="{http://schemas.android.com/apk/res/android}"

class SharedThemeContract(unittest.TestCase):
    def test_manifest_uses_app_theme(self):
        manifest=ET.parse(ROOT/"AndroidManifest.xml").getroot()
        self.assertEqual("@style/Theme.AiCalls", manifest.find("application").attrib[NS+"theme"])
    def test_all_buttons_receive_a_touch_target_and_ripple(self):
        root=ET.parse(ROOT/"res/values/styles.xml").getroot()
        styles={s.attrib["name"]:s for s in root.findall("style")}
        self.assertIn("Theme.AiCalls",styles)
        self.assertIn("Widget.AiCalls.Button",styles)
        items={i.attrib["name"]:i.text for i in styles["Widget.AiCalls.Button"].findall("item")}
        self.assertEqual("48dp",items["android:minHeight"])
        self.assertEqual("false",items["android:textAllCaps"])
        ripple=ET.parse(ROOT/"res/drawable/bg_aicalls_button.xml").getroot()
        self.assertEqual("ripple",ripple.tag)
if __name__=="__main__": unittest.main()
