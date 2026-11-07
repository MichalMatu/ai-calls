"""UX-04 audio summary only: preserve the original preference, probe and voice IO owners."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge/audioengines"

class AudioActivePresentationBoundaryTest(unittest.TestCase):
    def test_activity_only_formats_active_profile_and_keeps_existing_operations(self):
        activity = (ROOT / "AudioEngineSettingsActivity.kt").read_text()
        self.assertIn("AudioEngineActiveProfilePresenter.present(store.load())", activity)
        self.assertIn("AudioEngineActiveState.Invalid", activity)
        self.assertIn("R.string.eng_ui_summary", activity)
        for owner in ("store.save(requested)", "store.save(AudioEngineProfile.STABLE)",
                      "AudioEngineReadinessProbe.check", "ParakeetModelDownloader(this)",
                      "TextToSpeech(this", "previewVoice()", "getSystemService(TelecomManager::class.java)"):
            self.assertIn(owner, activity)

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
