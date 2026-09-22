import json
import unittest
from pathlib import Path

import local_phone_llm_live_call as live


ROOT = Path(__file__).resolve().parents[1]
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeMobileDataProblemSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_mobile_data_problem_action(self):
        self.assertEqual("mobile_data_problem", live.ORANGE_ACTION_MOBILE_DATA_PROBLEM)
        self.assertIn(live.ORANGE_ACTION_MOBILE_DATA_PROBLEM, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_MOBILE_DATA_PROBLEM, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Nie działają mi dane komórkowe.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_MOBILE_DATA_PROBLEM],
        )

    def test_mobile_data_problem_seed_remains_discovered_after_v256_root_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.mobile_data.problem"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("mobile_data_problem", seed["action_id"])
        self.assertEqual("Nie działają mi dane komórkowe.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual(
            "chatgpt-orange-mobile-data-problem-live-v256-20260922",
            seed["last_evidence"],
        )
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("closed_after_reviewed_root_reprompt", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
