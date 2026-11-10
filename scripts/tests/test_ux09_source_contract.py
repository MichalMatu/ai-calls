"""Source contracts for UX09: no data migration, no call/media side effects."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

def read(path):
    return (ROOT / path).read_text()

class Ux09Contract(unittest.TestCase):
    def test_receptionist_hides_raw_presets_and_advanced_keeps_history(self):
        basic = read("conversation/ConversationSkillsActivity.kt")
        advanced = read("conversation/ConversationSkillsAdvancedActivity.kt")
        for token in ("receptionist-tone-options", "receptionist-personal-edit",
                      "withPersonalInstructions", "skills_incoming_scope_footer"):
            self.assertIn(token, basic)
        self.assertNotIn("skills-inactive-toggle:", basic)
        self.assertIn("library.bindVersion", advanced)
        self.assertIn("skills-history-toggle:", advanced)

    def test_usb_has_single_header_and_no_double_frame(self):
        text = read("debug/UsbDebugPanelActivity.kt")
        self.assertNotIn('label(getString(R.string.usb_ui_title), 22f)', text)
        self.assertIn('background = null', text)
        self.assertIn('"usb-raw"', text)

    def test_campaign_result_is_not_a_multiline_blob(self):
        text = read("campaign/CampaignToolActivity.kt")
        for token in ('"campaign-last-result-card"', '"campaign-execution-summary"',
                      '"campaign-execution-timestamp"', '"campaign-result-explain"',
                      "lastExecutionExplanation.text = screen.summary"):
            self.assertIn(token, text)
        self.assertNotIn('lastExecutionStatus.text = getString(R.string.camp_ui_runtime_heading) +', text)
        for key in ("startButton", "stopSupervisedAudioButton", "revokeButton",
                    "recordEvidenceChoice", "evidenceSection"):
            self.assertIn(key, text)

    def test_home_keeps_chat_and_starts_without_idle_dock(self):
        text = read("MainActivity.kt")
        for token in ('"home-technical"', '"home-conversation-floating-overlay"',
                      "LiveConversationOverlay(this@MainActivity", "AppNavigation.bar"):
            self.assertIn(token, text)
        self.assertIn("startAuthorizedTaskButton", text)
        self.assertIn("stopAiButton", text)

    def test_agent_names_visible_but_never_plaintext(self):
        text = read("ui/AgentAiHubActivity.kt")
        for token in ("AndroidIdentityVault.create(this).availableFields().getOrThrow()",
                      "namedScope(outgoing.dataFields)", "namedScope(incoming.dataFields)",
                      "ux09_agent_scope_warning", "skills_hub_summary"):
            self.assertIn(token, text)
        self.assertNotIn("withPlaintext", text)
        self.assertNotIn(".get(IdentityFieldId.", text)

if __name__ == "__main__":
    unittest.main()
