"""Campaign task construction must stay out of the Activity and remain pure."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class PhoneTaskApprovalBoundary(unittest.TestCase):
    def test_approval_builder_is_pure_and_ui_keeps_permission_checks(self):
        ui = (ROOT / "campaign/PhoneTaskWizardActivity.kt").read_text()
        factory = (ROOT / "campaign/PhoneTaskApprovalFactory.kt").read_text()
        self.assertIn("PhoneTaskApprovalFactory.build(draft, brief(), now, speechLanguage)", ui)
        for expected in (
            "Manifest.permission.READ_PHONE_STATE", "Manifest.permission.CALL_PHONE",
            "CampaignAccountScope.forSubscription", "PhoneTaskScheduler.allowed(this)",
            "approvalGeneration == PhoneTaskWork.generation.get()",
        ):
            self.assertIn(expected, ui)
        for expected in (
            "PhoneTaskInput.instant(", "Math.multiplyExact(",
            "PhoneTaskInput.recipients(draft.recipients).values", ".validated()",
            "requireNotNull(draft.subscriptionId)", "recordAudio = draft.recordAudio",
        ):
            self.assertIn(expected, factory)
        for forbidden in ("import android.", "import androidx.", "UiDesign",
                          "startForegroundService(", "getSharedPreferences(",
                          "TelecomManager", "placeCall(", "PhoneTaskWork.store("):
            self.assertNotIn(forbidden, factory)


if __name__ == "__main__":
    unittest.main()
