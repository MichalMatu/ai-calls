"""F-039: launch an existing authorized campaign without redundant UI navigation.

Static source contract is intentionally device-free: it does not start a call,
modify any grant, or bypass Android Telecom/user permissions.
"""

import re
import unittest
from pathlib import Path


BASE = Path(__file__).resolve().parents[2] / (
    "app/src/main/kotlin/pl/michalmatu/aicallbridge"
)


class PrimaryTaskEntryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (BASE / "MainActivity.kt").read_text(encoding="utf-8")
        cls.campaign = (
            BASE / "campaign/CampaignToolActivity.kt"
        ).read_text(encoding="utf-8")
        cls.control = (BASE / "campaign/CampaignAgentControl.kt").read_text(encoding="utf-8")
        cls.service = (
            BASE / "campaign/AppOwnedCampaignService.kt"
        ).read_text(encoding="utf-8")

    def test_single_primary_start_uses_existing_campaign_screen_flow(self):
        self.assertIn('text = "Rozpocznij autoryzowane zadanie AI"', self.main)
        self.assertTrue(
            re.search(
                r"startAuthorizedTaskButton = Button\(this\).*?"
                r"startActivity\(\s*Intent\(this@MainActivity, "
                r"CampaignToolActivity::class\.java\)"
                r"\s*\.putExtra\(CampaignToolActivity\.EXTRA_START_AUTHORIZED_CAMPAIGN, true\)",
                self.main,
                flags=re.S,
            )
        )
        self.assertIn("addView(startAuthorizedTaskButton)", self.main)

    def test_start_accepts_exact_task_without_a_second_grant_dialog(self):
        self.assertIn("savedInstanceState == null", self.campaign)
        self.assertIn(
            "intent.getBooleanExtra(EXTRA_START_AUTHORIZED_CAMPAIGN, false)",
            self.campaign,
        )
        self.assertIn("ensurePermissionsThen(PendingAction.START)", self.campaign)
        self.assertIn("CampaignAuthorizationPolicy.matchesExactTask(", self.campaign)
        self.assertNotIn("showAuthorizationConfirmation", self.campaign)
        self.assertIn("ClirCampaignSpec.acceptEnableTask(", self.control)
        self.assertIn("store.save(ClirCampaignSpec.acceptEnableTask(", self.control)
        self.assertIn("requestCallRole(continueWithStart = true)", self.campaign)
        self.assertIn(
            ".setAction(AppOwnedCampaignService.ACTION_START_CLIR_ENABLE)",
            self.control,
        )
        self.assertIn("continuationStore.load()", self.service)
        self.assertIn("ACTION_STOP_ALL_AI", self.service)

    def test_supervised_pilot_remains_reachable_but_not_primary_start(self):
        advanced = self.main.split("val advancedSection = UiAccordionSection(", 1)[1].split(
            "developerProbes.addControls(advancedSection.controlsContainer)", 1
        )[0]
        self.assertIn("addControl(oneTapButton)", advanced)
        self.assertIn("addView(stopAiButton)", self.main)
        self.assertNotIn("addView(oneTapButton)", self.main)


if __name__ == "__main__":
    unittest.main()
