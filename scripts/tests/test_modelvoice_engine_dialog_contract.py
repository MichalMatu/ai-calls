"""Engine dialog: only the owner may save a new engine or trigger TTS reload."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ModelVoiceEngineDialogContract(unittest.TestCase):
    def test_owner_applies_selection_with_voice_reset(self):
        screen = (ROOT / "audioengines/ModelVoiceSettings.kt").read_text()
        for token in (
            "ModelVoiceEnginePickerPresenter.present(", "ModelVoiceEngineDialog(",
            "ttsEnginePackage = packageName, voiceId = null", "SpeechOutputId.ANDROID_TTS_PL",
            "onReturnToVoices = ::showVoicePicker", "save(draft.copy(",
        ):
            self.assertIn(token, screen)

    def test_dialog_only_renders_available_engines_and_forwards_callbacks(self):
        view = (ROOT / "ui/modelvoice/ModelVoiceEngineDialog.kt").read_text()
        for token in (
            "R.string.mv_engine", "options.labels.toTypedArray()", "options.selected",
            "onSelectEngine(options.ids[position])", "dialog.dismiss()",
            "onReturnToVoices()", "setNegativeButton(android.R.string.cancel)",
        ):
            self.assertIn(token, view)
        for forbidden in (
            "AudioEngineProfileStore(", "TextToSpeech(", "ModelCompatibilityProbe(",
            "TelecomManager", "getSharedPreferences(", "startForegroundService(",
            "placeCall(", "engine.speak(", "AudioEngineRegistry.incompatibility(",
        ):
            self.assertNotIn(forbidden, view)


if __name__ == "__main__":
    unittest.main()
