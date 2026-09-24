import unittest

from g5_clir_route_discovery_plan import (
    build_g5_clir_route_discovery_plan,
    validate_g5_clir_route_discovery_report,
)
from local_phone_llm_live_call import (
    ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
    ORANGE_SUPPORT_NUMBER,
)


class G5ClirRouteDiscoveryPlanTest(unittest.TestCase):
    def test_plan_is_read_only_and_requires_fresh_live_authorization(self):
        plan = build_g5_clir_route_discovery_plan()

        self.assertEqual(ORANGE_SUPPORT_NUMBER, plan.target)
        self.assertEqual(ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO, plan.primary_action)
        self.assertTrue(plan.observe_next)
        self.assertFalse(plan.external_effect_execution)
        self.assertFalse(plan.commitment_permit_use)
        self.assertTrue(plan.fresh_live_call_authorization_required)

    def test_plan_rejects_non_allowlisted_target(self):
        with self.assertRaises(ValueError):
            build_g5_clir_route_discovery_plan("501234567")

    def test_report_accepts_only_reviewed_info_turn_plus_nonblank_observation(self):
        observation = validate_g5_clir_route_discovery_report(
            {
                "orange_live_action": ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
                "approved_text": "Jak działa zastrzeganie numeru?",
                "orange_observation_text": "przykładowa odpowiedź operatora",
            }
        )
        self.assertEqual("przykładowa odpowiedź operatora", observation)

        invalid_reports = [
            {
                "orange_live_action": "caller_id_restriction_enable",
                "approved_text": "Jak działa zastrzeganie numeru?",
                "orange_observation_text": "odpowiedź",
            },
            {
                "orange_live_action": ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
                "approved_text": "inna wypowiedź",
                "orange_observation_text": "odpowiedź",
            },
            {
                "orange_live_action": ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
                "approved_text": "Jak działa zastrzeganie numeru?",
                "orange_observation_text": "   ",
            },
        ]
        for report in invalid_reports:
            with self.assertRaises(ValueError):
                validate_g5_clir_route_discovery_report(report)


if __name__ == "__main__":
    unittest.main()
