import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeOutgoingCallProblemSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_outgoing_call_problem_action(self):
        self.assertEqual("outgoing_call_problem", live.ORANGE_ACTION_OUTGOING_CALL_PROBLEM)
        self.assertIn(live.ORANGE_ACTION_OUTGOING_CALL_PROBLEM, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_OUTGOING_CALL_PROBLEM, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Nie mogę wykonywać połączeń.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_OUTGOING_CALL_PROBLEM],
        )

    def test_public_support_outgoing_call_problem_seed_is_discovered_only(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.voice.outgoing_calls"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("outgoing_call_problem", seed["action_id"])
        self.assertEqual("Nie mogę wykonywać połączeń.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-outgoing-call-problem-live-v228-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

    def test_v228_physically_verified_only_root_edge_to_known_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.outgoing_call_problem"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("outgoing_call_problem", edge["action_id"])
        self.assertEqual("Nie mogę wykonywać połączeń.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-outgoing-call-problem-live-v228-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])


if __name__ == "__main__":
    unittest.main()
