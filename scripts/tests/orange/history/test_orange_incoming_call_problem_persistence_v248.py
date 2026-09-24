import json
import unittest
from pathlib import Path


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeIncomingCallProblemPersistenceV248Test(unittest.TestCase):
    def test_v248_physically_verified_only_root_edge_to_known_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.incoming_call_problem"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("incoming_call_problem", edge["action_id"])
        self.assertEqual("Nie mogę odbierać połączeń.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual(
            "chatgpt-orange-incoming-call-problem-live-v248-20260922",
            edge["evidence_task"],
        )
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

    def test_v248_updates_seed_without_promoting_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.voice.incoming_calls"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual(
            "chatgpt-orange-incoming-call-problem-live-v248-20260922",
            seed["last_evidence"],
        )
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("closed_after_reviewed_root_reprompt", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
