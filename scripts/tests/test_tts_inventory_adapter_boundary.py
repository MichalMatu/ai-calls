"""Keep Android TTS inventory reads behind the data adapter, not the view."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class TtsInventoryAdapterBoundary(unittest.TestCase):
    def test_view_uses_inventory_adapter_but_does_not_enumerate_engine_state(self):
        source = (APP / "ui/modelvoice/ModelVoiceSettings.kt").read_text()
        self.assertIn("ttsSession.load(", source)
        self.assertNotIn("TextToSpeech.OnInitListener", source)
        self.assertIn("AndroidTtsVoiceSession(activity)", source)
        self.assertNotIn("val installed = engine.engines", source)
        self.assertNotIn("val voices = ModelVoiceTtsInventoryPolicy.availableOffline(", source)
        self.assertIn("voiceState.accepts(generation)", source)
        self.assertIn("voiceState.takeQueuedPreview()", source)

    def test_adapter_is_read_only_and_preserves_offline_policy(self):
        source = (APP / "data/adapters/AndroidTtsVoiceInventoryReader.kt").read_text()
        for required in (
            "val installed = engine.engines",
            "ModelVoiceTtsInventoryPolicy.availableOffline(",
            "selectedEngine = selectedEngine",
            "engine.voices.orEmpty()",
            "piperRequired = selectedEngine == PiperServiceContract.PACKAGE",
            "return Snapshot(installed, voices, engine.defaultEngine)",
        ):
            self.assertIn(required, source)
        for forbidden in ("import pl.michalmatu.aicallbridge.ui.", ".speak(", ".setVoice("):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
