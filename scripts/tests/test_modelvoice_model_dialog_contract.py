"""Model picker UI must not take over file verification, profile saving or model transfer."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ModelVoiceModelDialogContract(unittest.TestCase):
    def test_qualified_imports_remain_owner_responsibility(self):
        owner = (ROOT / "ui/modelvoice/ModelVoiceSettings.kt").read_text()
        for token in ("library.isInstalled(model)", "LiteRtModelContainer.hasHeader(file)",
                      "ModelCompatibilityStore(activity).verified(file, false)",
                      "ModelVoiceModelPickerPresenter.present(", "ModelVoiceModelDialog(",
                      "selectModel = ::selectModel", "manageFiles = manageFiles"):
            self.assertIn(token, owner)

    def test_dialog_only_renders_passed_options_and_forwards_choices(self):
        view = (ROOT / "ui/modelvoice/ModelVoiceModelDialog.kt").read_text()
        for token in ("picker.rows.map { it.title }", "picker.selected",
                      "selectModel(choice.dialogue, choice.customId)",
                      "if (saved) dialog.dismiss()", "listView.setItemChecked(picker.selected, true)",
                      "R.string.mv_manage_files", "setNegativeButton(android.R.string.cancel, null)"):
            self.assertIn(token, view)
        for forbidden in ("ModelCompatibilityStore(", "UserLiteRtModelStore(",
                          "LiteRtModelContainer.", "AudioEngineProfileStore(",
                          "startForegroundService(", "placeCall(", "TelecomManager"):
            self.assertNotIn(forbidden, view)


if __name__ == "__main__":
    unittest.main()
