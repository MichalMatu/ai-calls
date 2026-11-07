"""UX-04 read-only model presentation; legacy store/service keep mutation authority."""
import unittest
from pathlib import Path

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

class ModelPresentationBoundaryTest(unittest.TestCase):
    def test_activity_renders_typed_status_but_starts_original_service(self):
        activity = (BASE / "textagent/LanguageModelActivity.kt").read_text()
        self.assertIn("LanguageModelStatusPresenter.present(", activity)
        self.assertIn("row.card.isActivated = isActive", activity)
        self.assertIn("modelState.showSelect", activity)
        self.assertIn("modelState.progressPercent", activity)
        self.assertIn("AndroidGemma4ModelReadiness.check", activity)
        self.assertIn("LanguageModelTransferService.status.busy", activity)
        self.assertIn("startForegroundService(Intent(this, LanguageModelTransferService::class.java)", activity)
        self.assertIn("store.save(current.copy(dialogue = model.id))", activity)

    def test_presenter_is_readonly_and_separates_installed_from_selected(self):
        presenter = (BASE / "textagent/LanguageModelStatusPresenter.kt").read_text()
        for s in ("installed && selected == entry.id", "showProgress = transfer.busy",
                  "Collections.unmodifiableList", "transfer.message.takeIf"):
            self.assertIn(s, presenter)
        for unsafe in ("Context", "TelecomManager", "startService(", "placeCall(",
                       ".save(", "download(", "startForegroundService(", "getSharedPreferences("):
            self.assertNotIn(unsafe, presenter)

if __name__ == "__main__":
    unittest.main()
