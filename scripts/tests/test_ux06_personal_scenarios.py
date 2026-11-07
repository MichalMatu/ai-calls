"""UX-06: source contract for user-owned draft history without call authority."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class PersonalScenarioUiContract(unittest.TestCase):
    def test_ui_exposes_copies_versions_and_does_not_call_telephony(self):
        ui = (SRC / "conversation/ScenarioLibraryActivity.kt").read_text()
        for token in ('"scenario-personal:', "loadPersonalScenarios()",
                      "savePersonalScenarios(", "scenario_ui_save_version",
                      "scenario_ui_restore", "scenario_ui_duplicate",
                      "scenario_ui_delete", "scenario-personal-goal"):
            self.assertIn(token, ui)
        for forbidden in ("ACTION_START_CLIR_ENABLE", "ACTION_CALL", "placeCall(",
                          "CallCommitmentGate", "CampaignAgentControl.run("):
            self.assertNotIn(forbidden, ui)

    def test_home_one_tap_never_mislabels_clir_as_generic_scenario(self):
        home = (SRC / "MainActivity.kt").read_text()
        campaign = (SRC / "campaign/CampaignToolActivity.kt").read_text()
        pl = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        en = (ROOT / "app/src/main/res/values-en/strings.xml").read_text()
        self.assertIn("EXTRA_START_AUTHORIZED_CAMPAIGN", home)
        self.assertIn("requestedStartMode = CampaignDialogueMode.SCRIPT_GEMMA", campaign)
        self.assertIn('name="home_ui_start">Rozpocznij zadanie CLIR', pl)
        self.assertIn('name="home_ui_start">Start CLIR task', en)
        self.assertIn("home_ux06_direct_clir_scope", home)

    def test_personal_records_reuse_keystore_aead_and_atomic_write(self):
        store = (SRC / "conversation/AndroidConversationPreparationStore.kt").read_text()
        self.assertIn('writeRecord("scenario-personal-library-v1"', store)
        self.assertIn('readRecord("scenario-personal-library-v1")', store)
        self.assertIn("AndroidKeystoreIdentityVaultAead", store)
        self.assertIn("AtomicFile", store)
        self.assertIn("ScenarioPersonalLibrary", store)


if __name__ == "__main__":
    unittest.main()
