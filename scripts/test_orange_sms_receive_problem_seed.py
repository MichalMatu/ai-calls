import json
import unittest
from pathlib import Path

import local_phone_llm_live_call as live


ROOT = Path(__file__).resolve().parents[1]
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeSmsReceiveProblemSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_sms_receive_problem_action(self):
        self.assertEqual("sms_receive_problem", live.ORANGE_ACTION_SMS_RECEIVE_PROBLEM)
        self.assertIn(live.ORANGE_ACTION_SMS_RECEIVE_PROBLEM, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_SMS_RECEIVE_PROBLEM, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Nie mogę odbierać SMS-ów.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_SMS_RECEIVE_PROBLEM],
        )

    def test_public_support_sms_receive_problem_seed_remains_discovered_after_root_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.sms.receive"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("sms_receive_problem", seed["action_id"])
        self.assertEqual("Nie mogę odbierać SMS-ów.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual(
            "chatgpt-orange-sms-receive-problem-live-v240-20260922",
            seed["last_evidence"],
        )
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("closed_after_reviewed_root_reprompt", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
