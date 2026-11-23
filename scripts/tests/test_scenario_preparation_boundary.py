"""Scenario preparation is pure; UI retains localized messages and navigation only."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ScenarioPreparationBoundary(unittest.TestCase):
    def test_scenario_outgoing_handoff_is_pure_policy(self):
        activity = (ROOT / "conversation/ScenarioLibraryActivity.kt").read_text()
        policy = (ROOT / "presentation/scenarios/ScenarioCallPreparationPolicy.kt").read_text()
        for required in (
            "ScenarioCallPreparationPolicy.prepare(",
            "ScenarioCallPreparationPolicy.Result.Missing",
            "ScenarioCallPreparationPolicy.Result.Invalid",
            "ScenarioCallPreparationPolicy.Result.Ready",
            "ScenarioOutgoingCallActivity::class.java",
        ):
            self.assertIn(required, activity)
        for required in (
            "draft.missingRequiredFields(definition)",
            "title.trim()", "goal.trim()", "draft.values[field.id]?.trim()",
            "trimmedTitle.length !in 2..140", "trimmedGoal.length !in 5..300",
            "brief.length !in 5..3500",
        ):
            self.assertIn(required, policy)
        for forbidden in (
            "import android.", "import androidx.", "import pl.michalmatu.aicallbridge.ui.",
            "startActivity(", "placeCall(", "startService(", "getSharedPreferences(",
            "AppOwnedCampaignService", "TelecomManager",
        ):
            self.assertNotIn(forbidden, policy)


if __name__ == "__main__":
    unittest.main()
