"""Sprint3: stateful UI contracts and bilingual primary screens, no telephony effects."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
K = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
RES = ROOT / "app/src/main/res"

class UxSprint3Contract(unittest.TestCase):
    def read(self, relative):
        return (K / relative).read_text()

    def test_campaign_uses_one_start_and_separate_diagnostics(self):
        c = self.read("campaign/CampaignToolActivity.kt")
        self.assertIn('tag = "campaign-dialogue-picker"', c)
        self.assertNotIn("directStartButton", c)
        self.assertIn("ensurePermissionsThen(PendingAction.START)", c)
        self.assertIn('tag = "campaign-attempt-status"', c)
        self.assertIn("UiActionTone.REVOCATION", c)
        self.assertIn("ACTION_STOP_ALL_AI", c)

    def test_phone_vault_and_sim_redaction_stay_distinct(self):
        phone = self.read("identity/PhoneEnrollmentActivity.kt")
        ctrl = self.read("incoming/IncomingAssistantSettingsController.kt")
        self.assertIn("IdentityFieldId.PHONE", phone)
        self.assertIn("AndroidIdentityVault.create", phone)
        self.assertIn("maskPhoneAccountLabel", ctrl)
        self.assertIn("IncomingAssistantPolicyStore.accountKey", ctrl)
        self.assertIn("R.string.phone_ui_save", phone)

    def test_active_model_and_readonly_factory_skills(self):
        models = self.read("textagent/LanguageModelActivity.kt")
        skills = self.read("conversation/ConversationSkillsActivity.kt")
        self.assertIn("row.card.isActivated = isActive", models)
        self.assertIn("row.select.visibility", models)
        self.assertIn('tag = "receptionist-tone-options"', skills)
        self.assertIn("library.withReceptionistTone", skills)
        self.assertIn("library.withPersonalInstructions", skills)

    def test_controls_and_visual_capture_are_stateful(self):
        tools = self.read("tools/ConversationToolsActivity.kt")
        screen = (ROOT/"app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/AndroidVisualPolishAuditTest.kt").read_text()
        self.assertIn("save.isEnabled = false", tools)
        self.assertIn("getGlobalVisibleRect", screen)
        self.assertIn("ui-audit-20261107-sprint6", screen)
        self.assertIn("campaign_section_not_expanded", screen)

    def test_primary_ui_resource_names_match_pl_and_en(self):
        import xml.etree.ElementTree as ET
        a = ET.parse(RES/"values/strings.xml").getroot()
        b = ET.parse(RES/"values-en/strings.xml").getroot()
        pl = {e.attrib["name"] for e in a if e.tag == "string"}
        en = {e.attrib["name"] for e in b if e.tag == "string"}
        self.assertEqual(pl, en)
        for key in ("phone_ui_saved", "models_ui_active", "campaign_ui_start",
                    "skills_ui_factory_readonly", "usb_ui_summary", "lab_ui_title"):
            self.assertIn(key, pl)

if __name__ == "__main__":
    unittest.main()
