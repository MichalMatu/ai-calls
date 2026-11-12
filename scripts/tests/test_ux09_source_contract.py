"""Source contracts for UX09: no data migration, no call/media side effects."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

def read(path):
    return (ROOT / path).read_text()

class Ux09Contract(unittest.TestCase):
    def test_receptionist_hides_raw_presets_and_advanced_keeps_history(self):
        basic = read("conversation/ConversationSkillsActivity.kt")
        for token in ("receptionist-tone-options", "receptionist-personal-edit",
                      "withPersonalInstructions"):
            self.assertIn(token, basic)
        self.assertNotIn("skills-inactive-toggle:", basic)
        self.assertNotIn("skills-history-toggle:", basic)
        self.assertNotIn("skills_incoming_scope_footer", basic)

    def test_usb_has_single_header_and_no_double_frame(self):
        text = read("debug/UsbDebugPanelActivity.kt")
        self.assertNotIn('label(getString(R.string.usb_ui_title), 22f)', text)
        self.assertIn('val statusCard = UiDesign.card(this)', text)
        self.assertEqual(14, text.count('        item("'))
        self.assertIn('if (::state.isInitialized) refresh()', text)
        self.assertNotIn('UiAccordionSection', text)
        self.assertNotIn('usb_ui_refresh', text)
        self.assertNotIn('item("campaign"', text)
        self.assertNotIn('item("recording-current"', text)
        self.assertNotIn('"usb-raw"', text)
        self.assertNotIn('ClipboardManager', text)

    def test_campaign_result_is_bound_to_the_exact_grant(self):
        text = read("campaign/AppointmentMvpActivity.kt")
        self.assertIn('tag = "appointment-review-result"', text)
        self.assertIn("grant?.grantId == task.grantId", text)
        self.assertIn("store.review(task.grantId)", text)
        self.assertIn("ACTION_CANCEL_OUTGOING_TASK", text)

    def test_home_keeps_chat_and_starts_without_idle_dock(self):
        text = read("MainActivity.kt")
        for token in ('"home-conversation-floating-overlay"', "LiveConversationOverlay(this@MainActivity",
                      "AppNavigation.install", '"home-appointment-start"', '"home-stop-ai"'):
            self.assertIn(token, text)
        self.assertNotIn('"home-technical"', text)

    def test_agent_summaries_are_read_off_ui_thread_without_private_values(self):
        text = read("ui/AgentAiHubActivity.kt")
        for token in ("AndroidIdentityVault.create(applicationContext).availableFields().getOrThrow().size",
                      "worker.execute", "token != generation", "skills_hub_summary"):
            self.assertIn(token, text)
        self.assertNotIn("withPlaintext", text)
        self.assertNotIn(".get(IdentityFieldId.", text)

if __name__ == "__main__":
    unittest.main()
