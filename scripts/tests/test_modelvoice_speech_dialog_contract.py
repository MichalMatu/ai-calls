"""UI-only speech pickers preserve callback authority, labels, ordering, and safety."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ModelVoiceSpeechDialogContract(unittest.TestCase):
    def test_screen_retains_validation_and_selection_authority(self):
        owner = (ROOT / "ui/modelvoice/ModelVoiceSettings.kt").read_text()
        self.assertIn("ModelVoiceLanguageDialog(activity, draft.languageTag)", owner)
        self.assertIn("ModelVoiceSelectionPolicy.forLanguage(draft, selected)", owner)
        self.assertIn("ModelVoiceInputDialog(", owner)
        self.assertIn("ModelVoiceSpeechPickerPresenter.inputOptions(draft, AudioEngineRegistry.inputs)", owner)
        self.assertIn("::selectInput", owner)
        self.assertIn("val saved = selected == draft.languageTag ||", owner)
        self.assertIn("if (saved) AppLanguage.select(activity, selected.substringBefore('-'))", owner)

    def test_view_only_forwards_choices_and_does_not_write_or_probe(self):
        dialog = (ROOT / "ui/modelvoice/ModelVoiceSpeechDialogs.kt").read_text()
        for token in ('arrayOf("pl-PL", "en-US")', "values.indexOf(selectedLanguage)",
                      "R.string.mv_speech_language_pl", "R.string.mv_speech_language_en",
                      "if (onSelected(values[index])) dialog.dismiss()",
                      "choice.showAdvancedHeading", "choice.input.name",
                      "choice.selected", "choice.enabled",
                      "if (onSelected(choice.input)) dialog.dismiss()",
                      "setNegativeButton(android.R.string.cancel, null)"):
            self.assertIn(token, dialog)
        for forbidden in ("AudioEngineProfileStore(", "ModelCompatibilityProbe(",
                          "TextToSpeech(", "getSharedPreferences(", "TelecomManager",
                          "startForegroundService(", "placeCall("):
            self.assertNotIn(forbidden, dialog)


if __name__ == "__main__":
    unittest.main()
