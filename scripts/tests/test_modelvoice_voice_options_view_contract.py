"""Voice options are a rendering surface: no TTS, persistence, telephony or inference."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ModelVoiceVoiceOptionsViewContract(unittest.TestCase):
    def test_owner_retains_all_effectful_callbacks(self):
        screen = (ROOT / "ui/modelvoice/ModelVoiceSettings.kt").read_text()
        for token in (
            "ModelVoiceVoiceOptionsView(activity, options).render(",
            "ModelVoiceSpeechPickerPresenter.voiceOptions(",
            "onChooseEngine = { voiceDialog?.dismiss(); showEnginePicker() }",
            "if (save(draft.copy(voiceId = id))) voiceDialog?.dismiss()",
            "onPreview = ::preview",
            "private fun preview(voiceId:",
            "ModelVoiceTtsInventoryPolicy.availableOffline(",
        ):
            self.assertIn(token, screen)

    def test_view_only_renders_state_and_forwards_taps(self):
        view = (ROOT / "ui/modelvoice/ModelVoiceVoiceOptionsView.kt").read_text()
        for token in (
            "options.removeAllViews()", "picker.engineLabel", "picker.loading",
            "picker.missingVoice", "picker.rows.forEach", "choice.selected",
            "choice.position", "R.string.mv_voice_auto", "R.string.mv_local_voice",
            "onChooseEngine()", "onSelectVoice(id)", "onPreview(id)",
            "R.string.eng_ui_preview", "UiDesign.dp(activity, 48)",
        ):
            self.assertIn(token, view)
        for forbidden in (
            "AudioEngineProfileStore(", "TextToSpeech(", "ModelCompatibilityProbe(",
            "getSharedPreferences(", "TelecomManager", "startForegroundService(",
            "placeCall(", "engine.speak(", "AudioEngineRegistry.incompatibility(",
        ):
            self.assertNotIn(forbidden, view)


if __name__ == "__main__":
    unittest.main()
