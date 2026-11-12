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
                      "row.download.visibility", "row.import.visibility",
                      'tag = "model-details-toggle:"', "row.progress.visibility"):
            self.assertIn(token,source)
        self.assertIn("if (LanguageModelTransferService.status.busy)",source)
    def test_lab_state_and_safe_campaign(self):
        lab=(ROOT/"conversation/ConversationSkillLabActivity.kt").read_text()
        camp=(ROOT/"campaign/AppointmentMvpActivity.kt").read_text()
        for token in ('tag = "lab-stop"', 'tag = "lab-rating-group"',
                      "runButton.isEnabled = next.manualStartAllowed",
                      "sendButton.isEnabled = next.phase == SkillLabPhase.INPUT_READY",
                      "stopButton.visibility = if (!next.manualStartAllowed",
                      "runner.stop()"):
            self.assertIn(token,lab)
        runner=(ROOT/"conversation/SkillLabRunner.kt").read_text()
        self.assertIn("canStart = false", runner)
        self.assertIn("SkillLabPhase.STOPPED", runner)
        for token in ("UiActionTone.PRIMARY", "UiActionTone.DANGER",
                      'tag = "appointment-authorize-call"', "grants.save(task.grant(",
                      "ACTION_START_BOOK_APPOINTMENT"):
            self.assertIn(token,camp)
    def test_preparation_and_engine_keep_original_policy(self):
        prep=(ROOT/"conversation/ConversationPreparationActivity.kt").read_text()
        engine=(ROOT/"audioengines/ModelVoiceSettings.kt").read_text()
        for token in ("RadioGroup(this)", "store.save(selected)",
                      "confirmDiscard(", 'tag = "preparation-actions"'):
            self.assertIn(token,prep)
        for token in ("UiDesign.card", 'tag = "model-voice-compatibility-check"',
                      "AudioEngineRegistry.incompatibility(profile)", "store.save(profile)",
                      "private fun preview(voiceId:"):
            self.assertIn(token,engine)
        service=(ROOT/"audioengines/ParakeetModelTransferService.kt").read_text()
        models=(ROOT/"textagent/LanguageModelActivity.kt").read_text()
        self.assertIn("ParakeetModelDownloader(this)", service)
        self.assertIn("startForegroundService(Intent(this, ParakeetModelTransferService::class.java)", models)
        self.assertNotIn("downloadParakeet()", engine)
if __name__=="__main__": unittest.main()
