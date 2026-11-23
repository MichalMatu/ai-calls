"""Native TTS engine ownership cannot regress back into an Android view."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class TtsSessionLifecycleBoundary(unittest.TestCase):
    def test_native_session_owns_engine_and_worker(self):
        session = (ROOT / "data/adapters/AndroidTtsVoiceSession.kt").read_text()
        for required in (
            "TextToSpeech.OnInitListener", "AndroidTtsVoiceInventoryReader.read(",
            "worker.execute", "worker.shutdownNow()", "engine?.shutdown()",
            "candidate.shutdown()", "if (!accepts())", "current.setVoice(selected)",
            "current.speak(text,", "engine?.stop()",
        ):
            self.assertIn(required, session)
        self.assertNotIn("import pl.michalmatu.aicallbridge.ui.", session)

    def test_view_controls_permission_preview_and_generation_fence_only(self):
        view = (ROOT / "ui/modelvoice/ModelVoiceSettings.kt").read_text()
        for required in (
            "voiceState.accepts(generation)", "voiceState.takeQueuedPreview()",
            "voiceState.cancelPreview()", "ttsSession.load(",
            "ttsSession.preview(", "ttsSession.stop()", "ttsSession.close()",
            "TelecomManager::class.java", "READ_PHONE_STATE",
        ):
            self.assertIn(required, view)
        for forbidden in (
            "TextToSpeech.OnInitListener", "TextToSpeech(", "Executors.newSingleThreadExecutor",
            "AndroidTtsVoiceInventoryReader.read(", "tts?.shutdown()",
        ):
            self.assertNotIn(forbidden, view)


if __name__ == "__main__":
    unittest.main()
