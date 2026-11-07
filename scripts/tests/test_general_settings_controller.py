"""Basic AI settings use the existing store through a small controller, not widget-owned keys."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge/ui"
class GeneralSettingsControllerContract(unittest.TestCase):
    def test_persistence_separation(self):
        activity=(ROOT/"GeneralSettingsActivity.kt").read_text()
        controller=(ROOT/"GeneralSettingsController.kt").read_text()
        self.assertIn("GeneralSettingsController(this)",activity)
        self.assertIn("controller.selectMode(selected)",activity)
        self.assertIn("controller.selectTextModel(selected)",activity)
        self.assertNotIn("CallRuntimePreferences(",activity)
        self.assertIn("CallRuntimePreferences(context.applicationContext)",controller)
        self.assertIn("store.saveAudioMode",controller)
        self.assertIn("store.saveTextLlmProvider",controller)
if __name__=="__main__": unittest.main()
