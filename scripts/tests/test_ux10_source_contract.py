"""UX10 source gates: floating content-only chat, simplified lab and encrypted custom vault."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
def source(p):return (ROOT/p).read_text()

class Ux10SourceGate(unittest.TestCase):
    def test_one_floating_component_on_both_screens(self):
        overlay=source("conversation/LiveConversationOverlay.kt")
        self.assertIn("snapshot.active && snapshot.lines.isNotEmpty()",overlay)
        self.assertIn("0.84f",overlay)
        self.assertIn("conversation-floating-button",overlay)
        self.assertIn("conversation-floating-unread",overlay)
        self.assertIn("handler.removeCallbacks(pulse)",overlay)
        for p in ("MainActivity.kt",):
            self.assertIn("LiveConversationOverlay",source(p))
            self.assertNotIn("ConversationChatPanel(this@",source(p))

    def test_vault_preserves_typed_encryption_and_separates_custom_values(self):
        custom=source("identity/AndroidCustomIdentityVault.kt")
        manager=source("identity/IdentityVaultManagerActivity.kt")
        agent=source("ui/AgentAiHubActivity.kt")
        for required in ("AndroidKeystoreIdentityVaultAead","AtomicFile",
                         "noBackupFilesDir","cipher.encrypt","cipher.decrypt",
                         "custom_secret_too_long"):
            self.assertIn(required,custom)
        self.assertIn("AndroidIdentityVault.create(this)",manager)
        self.assertIn("CustomVaultKind.entries",manager)
        self.assertIn("IdentityVaultManagerActivity::class.java",agent)
        for forbidden in ("startActivity(Intent(this, AppOwnedCampaignService",
                          "withPlaintext","ConversationChatFeed","CallTask"):
            self.assertNotIn(forbidden,manager)
            self.assertNotIn(forbidden,custom)

    def test_lab_de_duplicates_without_losing_stop_metrics_or_timeline(self):
        lab=source("conversation/ConversationSkillLabActivity.kt")
        self.assertNotIn('tag = "lab-follow"',lab)
        self.assertNotIn("getString(R.string.lab_ui_intro)",lab)
        for required in ('"lab-conversation-card"','"lab-turn-timeline"',
                         "stopLab()", "scoreView.visibility = View.VISIBLE",
                         "userReadingOlderTurns"):
            self.assertIn(required,lab)

if __name__=="__main__":unittest.main()
