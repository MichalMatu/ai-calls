"""Incoming answering UI only owns presentation, not policy persistence or Telecom."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
K=ROOT/"kotlin/pl/michalmatu/aicallbridge/incoming"
class IncomingSettingsSeparation(unittest.TestCase):
    def test_policy_logic_moves_out_of_activity(self):
        view=(K/"IncomingAssistantSettingsActivity.kt").read_text()
        adapter=(K/"IncomingAssistantSettingsController.kt").read_text()
        self.assertIn("IncomingAssistantSettingsController(this)",view)
        self.assertIn("controller.save(selected, chosen, recording.isChecked)",view)
        for forbidden in ("IncomingAssistantPolicyStore(", "TelecomManager", "AndroidOwnedInCallService", "READ_PHONE_STATE"):
            self.assertNotIn(forbidden,view)
        for expected in ("IncomingAssistantPolicyStore(app)", "TelecomManager::class.java", "incomingPolicyChanged()", "recordEvidence"):
            self.assertIn(expected,adapter)
        self.assertIn("mode == AnsweringMode.AI && account == null",adapter)
    def test_all_new_resources_translated(self):
        pl=ET.parse(ROOT/"res/values/strings.xml").getroot()
        en=ET.parse(ROOT/"res/values-en/strings.xml").getroot()
        a={x.attrib["name"] for x in pl.findall("string")}
        b={x.attrib["name"] for x in en.findall("string")}
        keys={x for x in a if x.startswith("incoming_ui_")}
        self.assertGreaterEqual(len(keys),18)
        self.assertTrue(keys.issubset(b))
if __name__=="__main__": unittest.main()
