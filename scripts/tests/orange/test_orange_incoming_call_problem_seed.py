import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeIncomingCallProblemSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_incoming_call_problem_action(self):
        self.assertEqual("incoming_call_problem", live.ORANGE_ACTION_INCOMING_CALL_PROBLEM)
        self.assertIn(live.ORANGE_ACTION_INCOMING_CALL_PROBLEM, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_INCOMING_CALL_PROBLEM, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Nie mogę odbierać połączeń.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_INCOMING_CALL_PROBLEM],
        )

    def test_public_support_incoming_call_problem_seed_is_discovered_only(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.voice.incoming_calls"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("incoming_call_problem", seed["action_id"])
        self.assertEqual("Nie mogę odbierać połączeń.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual(
            "chatgpt-orange-incoming-call-problem-live-v248-20260922",
            seed["last_evidence"],
        )
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("closed_after_reviewed_root_reprompt", seed["next_evidence"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
