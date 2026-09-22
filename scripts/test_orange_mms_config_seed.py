import json
import unittest
from pathlib import Path

import local_phone_llm_live_call as live


ROOT = Path(__file__).resolve().parents[1]
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

    def test_public_support_mms_config_seed_is_discovered_only(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.mms.configuration"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("mms_config", seed["action_id"])
        self.assertEqual("Jak skonfigurować MMS w telefonie?", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertIn("physical_route_required", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
