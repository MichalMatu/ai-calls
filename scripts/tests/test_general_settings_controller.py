"""Permission settings cannot advertise provider preferences ignored by the real call flow."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class PermissionSettingsContract(unittest.TestCase):
    def test_permissions_have_explicit_actions_without_legacy_engine_preferences(self):
        activity=(ROOT/"ui/GeneralSettingsActivity.kt").read_text()
        self.assertIn("DialerRoleCard(this)",activity)
        self.assertIn("requestPermissions",activity)
        for old in ("CallRuntimePreferences", "CallAudioMode", "TextLlmProvider", "selectTextModel", "selectMode"):
            self.assertNotIn(old,activity)
        editor=(ROOT/"audioengines/ModelVoiceSettings.kt").read_text()
        self.assertIn("AudioEngineProfileStore(activity)",editor)
        self.assertIn("store.save(draft)",editor)
        self.assertNotIn("placeCall(",editor)
if __name__=="__main__": unittest.main()
