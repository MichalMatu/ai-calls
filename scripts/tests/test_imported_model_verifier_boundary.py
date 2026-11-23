"""Imported-model UI owns safety gates; pure state and native adapter own internals."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ImportedModelVerifierBoundary(unittest.TestCase):
    def test_native_check_is_not_in_activity(self):
        owner = (ROOT / "ui/modelvoice/ModelLibraryScreenActivity.kt").read_text()
        state = (ROOT / "presentation/modelvoice/ImportedModelVerificationState.kt").read_text()
        adapter = (ROOT / "data/adapters/AndroidImportedModelVerifier.kt").read_text()
        for item in (
            "safeToEditModels()", "library.isInstalled(model)",
            "importedModelState.begin(model.id)",
            "importedModelState.complete(model.id, failure)",
            "importedModelVerifier.check(file, candidate)",
        ):
            self.assertIn(item, owner)
        self.assertNotIn("ModelCompatibilityProbe(this)", owner)
        for item in ("if (checkingId != null) return false",
                     "if (checkingId != modelId) return false",
                     "failed.remove(modelId)", "failed[modelId] = failure"):
            self.assertIn(item, state)
        for unsafe in ("import android.", "import androidx.",
                       "ModelCompatibilityProbe(", "startForegroundService("):
            self.assertNotIn(unsafe, state)
        for item in ("ModelCompatibilityProbe(context)",
                     "nativeProbe.check(file, profile, audio = false",
                     "nativeProbe.close()"):
            self.assertIn(item, adapter)
        self.assertNotIn("import pl.michalmatu.aicallbridge.ui.", adapter)


if __name__ == "__main__":
    unittest.main()
