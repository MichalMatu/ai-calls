import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeCoverageSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_coverage_action(self):
        self.assertEqual("coverage_info", live.ORANGE_ACTION_COVERAGE_INFO)
        self.assertIn(live.ORANGE_ACTION_COVERAGE_INFO, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_COVERAGE_INFO, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Chcę sprawdzić zasięg.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_COVERAGE_INFO],
        )

    def test_v201_coverage_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.coverage_info"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("coverage_info", edge["action_id"])
        self.assertEqual("Chcę sprawdzić zasięg.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-coverage-live-v201-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.coverage.info"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("coverage_info", seed["action_id"])
        self.assertEqual("Chcę sprawdzić zasięg.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-coverage-live-v201-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
