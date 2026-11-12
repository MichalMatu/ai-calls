"""UX-05 parity, visible-text migration and accessibility contract."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main"
KOTLIN = APP / "kotlin/pl/michalmatu/aicallbridge"
PL = APP / "res/values/strings.xml"
EN = APP / "res/values-en/strings.xml"
SCREENS = (
    "MainActivity.kt",
    "audioengines/ModelVoiceSettings.kt",
    "campaign/ScenarioOutgoingCallActivity.kt",
    "campaign/AndroidCallControlsActivity.kt",
    "conversation/ConversationSkillsActivity.kt",
    "conversation/ConversationMessageView.kt",
    "developerrelay/SupervisedCallPilotActivity.kt",
    "textagent/LanguageModelActivity.kt",
)
PREFIXES = ("home_one_tap_", "home_mic_", "eng_ui_", "camp_ux05_", "pilot_ui_",
            "skills_ux05_", "models_ux05_", "chat_turn_", "chat_tx_", "dialer_ui_")
ALLOW_DATA = ("To jest próbka lokalnego głosu AI Calls.",
              "Przyjmij wiadomość dla właściciela telefonu.",
              " — własny")


def values(file):
    root = ET.parse(file).getroot()
    return {e.attrib["name"]: "".join(e.itertext()) for e in root.findall("string")}


class Ux05LocaleAccessibility(unittest.TestCase):
    def test_pl_en_complete_and_format_parameters_match(self):
        pl, en = values(PL), values(EN)
        self.assertEqual(set(pl), set(en))
        self.assertGreaterEqual(len(pl), 490)
        pattern = re.compile(r"%(?:\d+\$)?[sd]")
        for name in pl:
            if not name.startswith(PREFIXES):
                continue
            with self.subTest(name=name):
                self.assertTrue(pl[name].strip())
                self.assertTrue(en[name].strip())
                self.assertEqual(pattern.findall(pl[name]), pattern.findall(en[name]))

    def test_targeted_screen_literals_are_resources_not_inline_polish(self):
        diacritics = re.compile(r'"[^"]*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ][^"]*"')
        for relative in SCREENS:
            source = (KOTLIN / relative).read_text()
            self.assertIn("R.string.", source)
            for number, line in enumerate(source.splitlines(), 1):
                if line.lstrip().startswith("//"):
                    continue
                if any(token in line for token in ALLOW_DATA):
                    continue
                with self.subTest(file=relative, line=number):
                    self.assertFalse(diacritics.search(line), line)

    def test_read_only_accessibility_and_authority_boundaries(self):
        ui = (KOTLIN / "ui/UiDesign.kt").read_text()
        nav = (KOTLIN / "ui/AppNavigation.kt").read_text()
        main = (KOTLIN / "MainActivity.kt").read_text()
        campaign = (KOTLIN / "campaign/AppointmentMvpActivity.kt").read_text()
        pilot = (KOTLIN / "developerrelay/SupervisedCallPilotActivity.kt").read_text()
        self.assertIn("isAccessibilityHeading = true", ui)
        self.assertIn("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO", nav)
        self.assertIn("ACCESSIBILITY_LIVE_REGION_POLITE", main)
        self.assertIn("ACCESSIBILITY_LIVE_REGION_POLITE", campaign)
        self.assertIn("ACCESSIBILITY_LIVE_REGION_POLITE", pilot)
        for token in ("ACTION_CANCEL_OUTGOING_TASK", "authorizeAndStart()",
                      "grants.revoke", "CampaignAuthorizationPolicy.authorize("):
            self.assertIn(token, campaign)
        self.assertIn("session?.stopAudio(reason)", pilot)
        self.assertNotIn("FLAG_SECURE", ui)
        self.assertNotIn("FLAG_SECURE", nav)


if __name__ == "__main__":
    unittest.main()
