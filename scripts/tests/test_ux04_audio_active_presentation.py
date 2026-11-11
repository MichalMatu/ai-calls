"""UX-04 audio summary only: preserve the original preference, probe and voice IO owners."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge/audioengines"

class AudioActivePresentationBoundaryTest(unittest.TestCase):
    def test_one_editor_separates_choice_from_readiness_and_retains_offline_voice_io(self):
        alias = (ROOT / "AudioEngineSettingsActivity.kt").read_text()
        self.assertIn("AudioEngineSettingsActivity : LanguageModelActivity()", alias)
        editor = (ROOT / "ModelVoiceSettings.kt").read_text()
        for owner in ("store.save(draft)", "AudioEngineReadinessProbe.check", "TextToSpeech(activity",
                      "getSystemService(TelecomManager::class.java)", "effectiveTtsEnginePackage"):
            self.assertIn(owner, editor)
        self.assertNotIn("ParakeetModelDownloader(", editor)
        service = (ROOT / "ParakeetModelTransferService.kt").read_text()
        self.assertIn("ParakeetModelDownloader(this)", service)
        self.assertIn("startForeground(", service)

    def test_summary_is_readonly_without_android_or_effects(self):
        presenter = (ROOT / "AudioEngineActiveProfilePresenter.kt").read_text()
        self.assertIn("if (profile == null) return AudioEngineActiveState.Invalid", presenter)
        self.assertIn("profile.voiceId", presenter)
        for forbidden in ("Context", "TelecomManager", "TextToSpeech",
                          "store.save(", "getSharedPreferences(", "startActivity(",
                          "AudioEngineReadinessProbe.check(", "placeCall("):
            self.assertNotIn(forbidden, presenter)

if __name__ == "__main__":
    unittest.main()
