import json
import unittest
from pathlib import Path

import local_phone_llm_live_call as live


ROOT = Path(__file__).resolve().parents[1]
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"
EVIDENCE_TASK = "chatgpt-orange-manual-network-selection-live-v214-20260922"
BARRIER_PROMPT = (
    "rozumiem że twoja sprawa dotyczy aktywacji czy możesz jednak dokładnie "
    "powiedzieć o co chodzi"
)


class OrangeManualNetworkSelectionSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_manual_network_selection_action(self):
        self.assertEqual(
            "manual_network_selection",
            live.ORANGE_ACTION_MANUAL_NETWORK_SELECTION,
        )
        self.assertIn(
            live.ORANGE_ACTION_MANUAL_NETWORK_SELECTION,
            live.ORANGE_LIVE_ACTIONS,
        )
        self.assertIn(
            live.ORANGE_ACTION_MANUAL_NETWORK_SELECTION,
            live.GATE_C_ROOT_ACQUISITION_ACTIONS,
        )
        self.assertEqual(
            "Jak włączyć ręczny wybór sieci operatora?",
            live.ORANGE_REVIEWED_RESPONSES[
                live.ORANGE_ACTION_MANUAL_NETWORK_SELECTION
            ],
        )

    def test_v214_activation_clarification_barrier_is_durable_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))

        nodes = {node["id"]: node for node in tree["nodes"]}
        barrier = nodes["orange.activation.clarification_barrier"]
        self.assertEqual("VERIFIED", barrier["status"])
        self.assertEqual("ACTIVATION_CLARIFICATION_BARRIER", barrier["kind"])
        self.assertEqual(BARRIER_PROMPT, barrier["observed_prompt"])
        self.assertEqual(EVIDENCE_TASK, barrier["evidence_task"])

        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.manual_network_selection"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.activation.clarification_barrier", edge["to"])
        self.assertEqual("manual_network_selection", edge["action_id"])
        self.assertEqual("Jak włączyć ręczny wybór sieci operatora?", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("ACTIVATION_BARRIER", edge["risk"])
        self.assertEqual("ACTIVATION_CLARIFICATION_BARRIER", edge["observed_outcome"])
        self.assertEqual(EVIDENCE_TASK, edge["evidence_task"])
        self.assertEqual("trailing_silence", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.network.manual_selection"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("manual_network_selection", seed["action_id"])
        self.assertEqual(
            "Jak włączyć ręczny wybór sieci operatora?",
            seed["speech"],
        )
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual(EVIDENCE_TASK, seed["last_evidence"])
        self.assertEqual("ACTIVATION_CLARIFICATION_BARRIER", seed["last_outcome"])
        self.assertIn("closed_after_activation_barrier", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

        observations = {
            item["transcript"]: item for item in tree["root_acquisition_observations"]
        }
        preroll = observations["jakości orange"]
        self.assertEqual("VERIFIED", preroll["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", preroll["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", preroll["effect"])
        self.assertEqual(EVIDENCE_TASK, preroll["evidence_task"])


if __name__ == "__main__":
    unittest.main()
