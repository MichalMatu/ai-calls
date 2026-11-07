"""UX-05: manual call control UI is localized without modifying call authority."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AndroidCallControlsActivity.kt"
RES = ROOT / "app/src/main/res"
KEYS = set(["dialer_ui_dial","dialer_ui_number_required","dialer_ui_dial_error","dialer_ui_answer","dialer_ui_take_over","dialer_ui_resume_ai","dialer_ui_resume_failed","dialer_ui_answer_settings","dialer_ui_reject","dialer_ui_end_displayed","dialer_ui_no_call","dialer_ui_ringing","dialer_ui_active","dialer_ui_holding","dialer_ui_disconnected","dialer_ui_connecting","dialer_ui_assistant_status"])


class ManualCallLocaleContract(unittest.TestCase):
    def test_every_manual_action_and_status_has_both_locales(self):
        tables = {}
        for directory in ("values", "values-en"):
            root = ET.parse(RES / directory / "strings.xml").getroot()
            tables[directory] = {e.attrib["name"]: e.text or "" for e in root.findall("string")}
        self.assertEqual(set(tables["values"]), set(tables["values-en"]))
        for key in KEYS:
            for directory in tables:
                with self.subTest(key=key, locale=directory):
                    self.assertTrue(tables[directory][key].strip())
            fmt = lambda s: re.findall(r"%(?:\d+\$)?[sd]", s)
            self.assertEqual(fmt(tables["values"][key]), fmt(tables["values-en"][key]))

    def test_text_only_migration_keeps_existing_call_controls(self):
        code = ACTIVITY.read_text()
        for key in KEYS:
            self.assertIn("R.string." + key, code)
        self.assertIn("R.string.phone_ui_field", code)
        for token in ("placeCall(", "call.answer(0)", "call.reject(false, null)",
                      "currentCall()?.disconnect()", "takeOverIncoming(call)",
                      "resumeIncoming(call)", "ACTION_PAUSE_AFTER_TAKEOVER"):
            self.assertIn(token, code)
        for untranslated in ("Zadzwoń (ręcznie)", "Przejmij rozmowę — zatrzymaj AI",
                             "Brak trwającego połączenia", "Asystent: $it"):
            self.assertNotIn(untranslated, code)


if __name__ == "__main__":
    unittest.main()
