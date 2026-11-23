"""Manifest-to-screen documentation drift guard (source-only; no device needed)."""
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
MAP = ROOT / "docs/architecture/UI_SCREEN_MAP.md"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class UiScreenMapContract(unittest.TestCase):
    def test_every_manifest_activity_is_documented(self):
        manifest = ElementTree.parse(MANIFEST).getroot()
        activities = [
            node.attrib[ANDROID + "name"]
            for node in manifest.iter("activity")
        ]
        self.assertTrue(activities)
        self.assertEqual(len(activities), len(set(activities)), "duplicate manifest Activity")
        doc = MAP.read_text()
        missing = [name for name in activities if f"`{name}`" not in doc]
        self.assertEqual(missing, [], "undocumented Android Activities")

    def test_bottom_navigation_and_nested_screen_contract(self):
        doc = MAP.read_text()
        for tab in ("Home", "History", "Agent", "Settings"):
            self.assertIn(f"**{tab}**", doc)
        for owner in (
            "ModelLibraryScreenActivity", "ModelVoiceSettings",
            "ConversationSkillLabActivity", "PhoneTaskWizardActivity",
            "ConversationHistoryActivity",
        ):
            self.assertIn(owner, doc)
        for safety in ("STOP/takeover", "SIM", "privacy", "legacy compatibility"):
            self.assertIn(safety, doc)


if __name__ == "__main__":
    unittest.main()
