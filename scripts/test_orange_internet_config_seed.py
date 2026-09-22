import json
import unittest
from pathlib import Path

import local_phone_llm_live_call as live


ROOT = Path(__file__).resolve().parents[1]
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeInternetConfigSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_internet_config_action(self):
        self.assertEqual("internet_config", live.ORANGE_ACTION_INTERNET_CONFIG)
        self.assertIn(live.ORANGE_ACTION_INTERNET_CONFIG, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_INTERNET_CONFIG, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Jak skonfigurować internet w telefonie?",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_INTERNET_CONFIG],
        )

    def test_v205_internet_config_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        nodes = {node["id"]: node for node in tree["nodes"]}
        reprompt = nodes["orange.root.reprompt"]
        self.assertIn(
            "przepraszam nie zrozumiałem czy możesz jeszcze raz powiedzieć o co chodzi",
            reprompt["observed_prompt_variants"],
        )
        self.assertEqual(
            "chatgpt-orange-internet-config-live-v205-20260922",
            reprompt["latest_variant_evidence_task"],
        )

        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.internet_config"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("internet_config", edge["action_id"])
        self.assertEqual("Jak skonfigurować internet w telefonie?", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-internet-config-live-v205-20260922", edge["evidence_task"])
        self.assertEqual("trailing_silence", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.internet.configuration"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("internet_config", seed["action_id"])
        self.assertEqual("Jak skonfigurować internet w telefonie?", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-internet-config-live-v205-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
