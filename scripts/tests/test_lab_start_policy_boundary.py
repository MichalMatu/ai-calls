"""Lab Activity delegates run validation and mode choice to a pure policy."""
from pathlib import Path
import unittest

APP = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge/conversation"


class LabStartPolicyBoundary(unittest.TestCase):
    def test_start_gate_is_pure_and_view_keeps_only_error_rendering(self):
        owner = (APP / "ConversationSkillLabActivity.kt").read_text()
        policy = (APP / "SkillLabStartPolicy.kt").read_text()
        self.assertIn("SkillLabStartPolicy.decide(", owner)
        self.assertIn("SkillLabStartPolicy.Decision.CaseRequired ->", owner)
        self.assertIn("SkillLabStartPolicy.Decision.InvalidBudget ->", owner)
        self.assertIn("sessionFactory.contextTokenBudget = budget", owner)
        self.assertIn("sessionFactory.skillLoadingMode = loadingMode", owner)
        for required in (
            "contextBudgetText.trim()", "value.toIntOrNull()", "adaptive && !hasCase",
            "SkillLoadingMode.CONTEXTUAL", "SkillLoadingMode.ALL_ACTIVE",
            "budget == null || budget <= 0",
        ):
            self.assertIn(required, policy)
        for forbidden in ("android.", "androidx.", "Activity", "startActivity(", "getSharedPreferences(",
                          "placeCall(", "startForegroundService("):
            self.assertNotIn(forbidden, policy)


if __name__ == "__main__":
    unittest.main()
