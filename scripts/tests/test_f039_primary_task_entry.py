"""Current task entry preserves explicit scope and permission continuation.

Host-only checks never create a grant, invoke Telecom or access a device.
"""
import unittest
from pathlib import Path

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class PrimaryTaskEntryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (BASE / "MainActivity.kt").read_text()
        cls.appointment = (BASE / "campaign/AppointmentMvpActivity.kt").read_text()

    def test_home_opens_the_form_without_granting_or_calling(self):
        self.assertIn("PhoneTaskWizardActivity::class.java", self.main)
        self.assertEqual(1, self.main.count('tag = "home-task-start"'))
        for forbidden in ("placeCall(", "grants.save(", "ACTION_START_BOOK_APPOINTMENT"):
            self.assertNotIn(forbidden, self.main)

    def test_explicit_start_keeps_exact_scope_and_android_permission_continuation(self):
        for token in ('tag = "appointment-authorize-call"', "authorizeAndStart()",
                      "pendingAuthorize = true", "if (code == 902 && pendingAuthorize)",
                      "results.all { it == PackageManager.PERMISSION_GRANTED }",
                      "if (hadReviewedSim) authorizeAndStart()", "CampaignAuthorizationPolicy.authorize(",
                      "selectedFields.filterValues { it.isChecked }", "grants.save(task.grant(",
                      "ACTION_START_BOOK_APPOINTMENT"):
            self.assertIn(token, self.appointment)
        self.assertNotIn("showAuthorizationConfirmation", self.appointment)
        resume = self.appointment.split("override fun onResume()", 1)[1].split("override fun onPause()", 1)[0]
        self.assertNotIn("authorizeAndStart()", resume)

    def test_stop_and_takeover_remain_reachable(self):
        self.assertIn("ACTION_STOP_ALL_AI", self.main)
        self.assertIn("ACTION_CANCEL_OUTGOING_TASK", self.appointment)
        self.assertIn("AppOwnedClirCampaignExecutor.takeOver(active)", self.appointment)
        self.assertIn("ACTION_PAUSE_AFTER_TAKEOVER", self.appointment)
        self.assertIn("AppNavigation.install", self.main)


if __name__ == "__main__":
    unittest.main()
