"""UX11: last purely presentational corrections, no added call or vault authority."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

class Ux11FinalPolish(unittest.TestCase):
    def test_lab_short_heading_and_empty_state_without_extra_intro(self):
        lab = (BASE / "conversation/ConversationSkillLabActivity.kt").read_text()
        self.assertIn("R.string.ux11_lab_timeline_title", lab)
        self.assertIn('"lab-empty-transcript"', lab)
        self.assertIn('emptyTranscript.visibility = if (next.completed.isEmpty()', lab)
        self.assertIn('progress.visibility = View.GONE', lab)
        self.assertIn("stopButton.visibility = if (!next.manualStartAllowed", lab)
        self.assertNotIn("R.string.lab_ui_timeline_title", lab)
        self.assertNotIn("R.string.lab_ui_intro", lab)
        self.assertIn("sendButton.isEnabled = next.phase == SkillLabPhase.INPUT_READY", lab)

    def test_vault_rows_compact_and_custom_action_at_top(self):
        vault = (BASE / "identity/IdentityVaultManagerActivity.kt").read_text()
        self.assertIn('tag = "vault-add-custom"', vault)
        self.assertIn("minimumHeight = UiDesign.dp(this@IdentityVaultManagerActivity, 62)", vault)
        self.assertIn("getString(R.string.ux11_vault_edit_field", vault)
        self.assertIn("getString(R.string.ux11_vault_remove_field", vault)
        self.assertLess(vault.index('tag = "vault-add-custom"'), vault.index("for (field in IdentityFieldId.entries)"))
        self.assertIn("predefined.availableFields()", vault)
        self.assertIn("custom.list()", vault)
        self.assertNotIn("withPlaintext", vault)

    def test_active_chat_uses_only_synthetic_feed(self):
        test = (Path(__file__).resolve().parents[2] /
            "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/conversation/AndroidUx11FloatingSyntheticTest.kt").read_text()
        for token in ("ConversationChatFeed.begin()", "ConversationChatFeed.heard(",
                      "ConversationChatFeed.replied(", "ConversationChatFeed.finish(",
                      '"chat-active-synthetic.jpg"'):
            self.assertIn(token, test)
        for dangerous in ("placeCall(", "ACTION_CALL", "startService(", "withPlaintext",
                          "requestPermissions(", "startRecording("):
            self.assertNotIn(dangerous, test)

if __name__ == "__main__":
    unittest.main()
