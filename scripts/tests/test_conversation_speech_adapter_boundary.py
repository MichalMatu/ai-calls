"""Runtime owns Call safety; Android provider composition belongs to data."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ConversationSpeechAdapterBoundary(unittest.TestCase):
    def test_runtime_delegates_native_provider_selection(self):
        runtime = (ROOT / "conversation/AndroidConversationRuntime.kt").read_text()
        self.assertIn("AndroidConversationSpeechPipelineFactory.create(", runtime)
        self.assertIn("runConfiguration.audioProfile", runtime)
        self.assertIn("private val pipeline =", runtime)
        for forbidden in (
            "SpeechInputId.ANDROID_STT ->",
            "SpeechOutputId.PIPER_PL ->",
            "AndroidSttInputProvider(context,",
            "LocalSpeechTextPipeline(\n",
        ):
            self.assertNotIn(forbidden, runtime)
        for required in ("authorizedNow()", "outputEpoch", "finish(termination: ConversationTermination)"):
            self.assertIn(required, runtime)

    def test_adapter_preserves_routes_and_rejects_unsupported_output(self):
        adapter = (ROOT / "data/adapters/AndroidConversationSpeechPipelineFactory.kt").read_text()
        for required in (
            "LocalSpeechTextPipeline(",
            "backend,", "approvalPolicy,", "profile.languageTag", "profile.voiceId",
            "SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(",
            "SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(",
            "SpeechInputId.GEMMA_AUDIO_DIRECT -> GemmaAudioDirectInputProvider()",
            "SpeechOutputId.ANDROID_TTS_PL -> AndroidTtsOutputProvider(",
            "SpeechOutputId.PIPER_PL -> PiperAndroidTtsOutputProvider(",
            "SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS ->",
            'error("unavailable_polish_output_engine")',
            "profile.effectiveTtsEnginePackage",
        ):
            self.assertIn(required, adapter)
        self.assertNotIn("import pl.michalmatu.aicallbridge.ui.", adapter)
        self.assertNotIn("android.telecom", adapter)


if __name__ == "__main__":
    unittest.main()
