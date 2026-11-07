"""Presentation contracts for the corrective sprint; no effects or preference changes."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class UiPolishSprintContract(unittest.TestCase):
    def test_shared_action_hierarchy_and_dynamic_gaps(self):
        design=(ROOT/"ui/UiDesign.kt").read_text()
        chrome=(ROOT/"ui/UiChromeActivity.kt").read_text()
        for token in ("UiActionTone", "PRIMARY", "SECONDARY", "DANGER",
                      "fun styleAction", "fun card", "GAP_ACTION", "CONTENT_INSET"):
            self.assertIn(token,design)
        self.assertIn("OnGlobalLayoutListener",chrome)
    def test_model_state_drives_visibility(self):
        source=(ROOT/"textagent/LanguageModelActivity.kt").read_text()
        for token in ('tag = "model-card:"', 'tag = "model-transfer-cancel"',
                      "row.download.visibility", "row.import.visibility", "row.select.visibility",
                      'tag = "model-details:"', "row.progress.visibility"):
            self.assertIn(token,source)
        self.assertIn("if (LanguageModelTransferService.status.busy)",source)
    def test_lab_state_and_safe_campaign(self):
        lab=(ROOT/"conversation/ConversationSkillLabActivity.kt").read_text()
        camp=(ROOT/"campaign/CampaignToolActivity.kt").read_text()
        for token in ('tag = "lab-stop"', 'tag = "lab-rating-group"',
                      'runButton.isEnabled = false', "stopButton.visibility = android.view.View.VISIBLE"):
            self.assertIn(token,lab)
        for token in ("UiActionTone.PRIMARY", "UiActionTone.DANGER",
                      'tag = "campaign-primary-actions"', "initiallyExpanded = false",
                      "ensurePermissionsThen(PendingAction.START)"):
            self.assertIn(token,camp)
    def test_preparation_and_engine_keep_original_policy(self):
        prep=(ROOT/"conversation/ConversationPreparationActivity.kt").read_text()
        engine=(ROOT/"audioengines/AudioEngineSettingsActivity.kt").read_text()
        for token in ("UiSelect.setOptions", "store.save(selection())",
                      "ConversationInstructionBuilder.build", 'tag = "preparation-actions"'):
            self.assertIn(token,prep)
        for token in ("UiDesign.card", 'tag = "engine-unavailable:"',
                      "AudioEngineReadinessProbe.check", "store.save(requested)",
                      "cancelDownloadButton.visibility = View.GONE"):
            self.assertIn(token,engine)
if __name__=="__main__": unittest.main()
