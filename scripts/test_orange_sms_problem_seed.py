import json
import unittest
from pathlib import Path

import local_phone_llm_live_call as live


ROOT = Path(__file__).resolve().parents[1]
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeSmsProblemSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_sms_problem_action(self):
        self.assertEqual("sms_problem", live.ORANGE_ACTION_SMS_PROBLEM)
        self.assertIn(live.ORANGE_ACTION_SMS_PROBLEM, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_SMS_PROBLEM, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Nie mogę wysyłać SMS-ów.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_SMS_PROBLEM],
        )

    def test_v218_sms_problem_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))

        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.sms_problem"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("sms_problem", edge["action_id"])
        self.assertEqual("Nie mogę wysyłać SMS-ów.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-sms-problem-live-v218-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.sms.problem"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("sms_problem", seed["action_id"])
        self.assertEqual("Nie mogę wysyłać SMS-ów.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-sms-problem-live-v218-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

        acquisition = {
            observation["transcript"]: observation
            for observation in tree["root_acquisition_observations"]
        }
        preroll = acquisition["orange"]
        self.assertEqual("VERIFIED", preroll["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", preroll["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", preroll["effect"])
        self.assertEqual("chatgpt-orange-sms-problem-live-v218-20260922", preroll["evidence_task"])


if __name__ == "__main__":
    unittest.main()
