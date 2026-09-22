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

    def test_public_support_mobile_data_problem_seed_is_discovered_only(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.mobile_data.problem"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("mobile_data_problem", seed["action_id"])
        self.assertEqual("Nie działają mi dane komórkowe.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("public_orange_support_page", seed["last_evidence"])
        self.assertEqual("NOT_PHYSICALLY_PROBED", seed["last_outcome"])
        self.assertIn("physical_route_required", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
