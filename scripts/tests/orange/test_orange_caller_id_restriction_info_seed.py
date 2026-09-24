import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeCallerIdRestrictionInfoSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_caller_id_restriction_info_action(self):
        self.assertEqual(
            "caller_id_restriction_info",
            live.ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
        )
        self.assertIn(live.ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(
            live.ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
            live.GATE_C_ROOT_ACQUISITION_ACTIONS,
        )
        self.assertEqual(
            "Jak działa zastrzeganie numeru?",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO],
        )

    def test_caller_id_restriction_info_seed_stays_discovered_after_v264_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.caller_id.restriction_info"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("caller_id_restriction_info", seed["action_id"])
        self.assertEqual("Jak działa zastrzeganie numeru?", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual(
            "chatgpt-orange-caller-id-restriction-info-live-v264-20260922",
            seed["last_evidence"],
        )
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("closed_after_reviewed_root_reprompt", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
