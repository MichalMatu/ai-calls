"""Refactor contract: generation editor remains a view, not a model or telephony owner."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ModelVoiceGenerationDialogContract(unittest.TestCase):
    def test_owner_remains_the_only_profile_save_authority(self):
        owner = (ROOT / "ui/modelvoice/ModelVoiceSettings.kt").read_text()
        dialog = (ROOT / "ui/modelvoice/ModelVoiceGenerationDialog.kt").read_text()
        self.assertIn("ModelVoiceGenerationDialog(activity, draft.generation)", owner)
        self.assertIn("save(draft.copy(generation = generation))", owner)
        self.assertIn("private val saveGeneration: (DialogueGenerationSettings) -> Boolean", dialog)
        self.assertNotIn("AudioEngineProfileStore(", dialog)
        self.assertNotIn("getSharedPreferences(", dialog)
        for forbidden in ("TextToSpeech(", "TelecomManager", "startService(",
                          "startForegroundService(", "placeCall("):
            self.assertNotIn(forbidden, dialog)

    def test_same_field_identity_defaults_validation_and_nonclosing_error(self):
        dialog = (ROOT / "ui/modelvoice/ModelVoiceGenerationDialog.kt").read_text()
        for field in (
            "R.string.model_generation_context", "R.string.model_generation_output",
            "R.string.model_generation_temperature", "R.string.model_generation_top_k",
            "R.string.model_generation_top_p",
        ):
            self.assertIn(field, dialog)
        for token in (
            "activity.resources.getResourceEntryName(label)", "InputType.TYPE_CLASS_NUMBER",
            "InputType.TYPE_NUMBER_FLAG_DECIMAL", "DialogueGenerationInputParser.parse(",
            "saveGeneration(DialogueGenerationSettings.DEFAULTS)", "if (next == null)",
            "R.string.model_generation_invalid", "if (saveGeneration(next)) dialog.dismiss()",
            "setNegativeButton(android.R.string.cancel, null)",
        ):
            self.assertIn(token, dialog)


if __name__ == "__main__":
    unittest.main()
