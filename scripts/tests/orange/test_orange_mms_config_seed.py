import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeMmsConfigSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_mms_config_action(self):
        self.assertEqual("mms_config", live.ORANGE_ACTION_MMS_CONFIG)
        self.assertIn(live.ORANGE_ACTION_MMS_CONFIG, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_MMS_CONFIG, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Jak skonfigurować MMS w telefonie?",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_MMS_CONFIG],
        )

    def test_v209_mms_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        nodes = {node["id"]: node for node in tree["nodes"]}
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.mms_config"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("mms_config", edge["action_id"])
        self.assertEqual("Jak skonfigurować MMS w telefonie?", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-mms-config-live-v209-20260922", edge["evidence_task"])
        self.assertEqual("trailing_silence", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

        reprompt = nodes["orange.root.reprompt"]
        self.assertIn(
            "niestety nie jestem pewien w czym mogę ci pomóc czy możesz dokładniej opisać problem",
            reprompt["observed_prompt_variants"],
        )
        self.assertEqual(
            "chatgpt-orange-mms-config-live-v209-20260922",
            reprompt["latest_variant_evidence_task"],
        )

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.mms.configuration"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("mms_config", seed["action_id"])
        self.assertEqual("Jak skonfigurować MMS w telefonie?", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-mms-config-live-v209-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

        prerolls = {
            item["transcript"]: item for item in tree["root_acquisition_observations"]
        }
        preroll = prerolls["5g jakości orange"]
        self.assertEqual("VERIFIED", preroll["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", preroll["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", preroll["effect"])
        self.assertEqual("chatgpt-orange-mms-config-live-v209-20260922", preroll["evidence_task"])


if __name__ == "__main__":
    unittest.main()
