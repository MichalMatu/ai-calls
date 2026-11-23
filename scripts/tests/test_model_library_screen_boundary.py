"""UI ownership and stable Android entrypoint after splitting model library screen.

Source-only checks: no ADB, download, phone calls or native model inference.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


class ModelLibraryScreenBoundary(unittest.TestCase):
    def test_legacy_activity_is_a_small_compatibility_entrypoint(self):
        shim = (MAIN / "textagent/LanguageModelActivity.kt").read_text()
        self.assertIn(
            "open class LanguageModelActivity : ModelLibraryScreenActivity()", shim
        )
        self.assertIn(
            "import pl.michalmatu.aicallbridge.ui.modelvoice.ModelLibraryScreenActivity", shim
        )
        self.assertLess(len(shim.splitlines()), 20)
        for forbidden in (
            "onCreate(", "TextToSpeech(", "startForegroundService(",
            "android.widget.", "AudioEngineProfileStore(", "placeCall(",
        ):
            self.assertNotIn(forbidden, shim)

    def test_screen_is_in_ui_and_preserves_navigation_and_cancellation(self):
        screen = (MAIN / "ui/modelvoice/ModelLibraryScreenActivity.kt").read_text()
        self.assertIn("package pl.michalmatu.aicallbridge.ui.modelvoice", screen)
        self.assertIn(
            "open class ModelLibraryScreenActivity : UiChromeActivity(AppTab.SETTINGS)", screen
        )
        self.assertIn("Intent(this, LanguageModelActivity::class.java).putExtra(FILES_MODE, true)", screen)
        for safety in (
            "private fun safeToEditModels(", "private fun updateParakeetUi(",
            "startForegroundService(Intent(this, ParakeetModelTransferService::class.java)",
            "ParakeetModelTransferService.CANCEL",
            "override fun onDestroy()", "configuration.close()",
        ):
            self.assertIn(safety, screen)

    def test_manifest_and_audio_entrypoint_keep_original_class(self):
        manifest = MANIFEST.read_text()
        self.assertIn('android:name=".textagent.LanguageModelActivity"', manifest)
        audio_alias = (MAIN / "audioengines/AudioEngineSettingsActivity.kt").read_text()
        self.assertIn("AudioEngineSettingsActivity : LanguageModelActivity()", audio_alias)


if __name__ == "__main__":
    unittest.main()
