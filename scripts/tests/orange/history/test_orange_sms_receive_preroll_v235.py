import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeSmsReceivePrerollV235Test(unittest.TestCase):
    def test_exact_v235_preroll_is_bounded_retry_allowlisted(self):
        self.assertIn("wie jakości orange", live.GATE_C_IGNORABLE_PREROLLS)
        report = {
            "gate_c_fast_path": "true",
            "gate_c_call_plan_bound": "true",
            "orange_live_action": live.ORANGE_ACTION_SMS_RECEIVE_PROBLEM,
            "backend_generate_calls": "0",
            "local_text_llm_live_call_success": "false",
            "failure_reason": "gate_c_take_over",
            "stt_text": "wie jakości orange",
        }
        self.assertTrue(live._is_ignorable_gate_c_preroll(report))

    def test_v235_preroll_evidence_persists_after_v240_root_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        observations = [
            item for item in tree["root_acquisition_observations"]
            if item.get("transcript") == "wie jakości orange"
        ]
        self.assertEqual(1, len(observations))
        observation = observations[0]
        self.assertEqual("VERIFIED", observation["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", observation["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", observation["effect"])
        self.assertEqual(
            "chatgpt-orange-sms-receive-problem-live-v235-20260922",
            observation["evidence_task"],
        )

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.sms.receive"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual(
            "chatgpt-orange-sms-receive-problem-live-v240-20260922",
            seed["last_evidence"],
        )
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("closed_after_reviewed_root_reprompt", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
