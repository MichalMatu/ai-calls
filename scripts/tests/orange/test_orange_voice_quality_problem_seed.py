import json
import unittest
from pathlib import Path

from aicall_tools.calls import local_phone_llm_live_call as live


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"
EVIDENCE_TASK = "chatgpt-orange-voice-quality-problem-live-v223-20260922"


class OrangeVoiceQualityProblemSeedTest(unittest.TestCase):
    def test_runner_exposes_reviewed_voice_quality_problem_action(self):
        self.assertEqual("voice_quality_problem", live.ORANGE_ACTION_VOICE_QUALITY_PROBLEM)
        self.assertIn(live.ORANGE_ACTION_VOICE_QUALITY_PROBLEM, live.ORANGE_LIVE_ACTIONS)
        self.assertIn(live.ORANGE_ACTION_VOICE_QUALITY_PROBLEM, live.GATE_C_ROOT_ACQUISITION_ACTIONS)
        self.assertEqual(
            "Podczas rozmów zanika głos.",
            live.ORANGE_REVIEWED_RESPONSES[live.ORANGE_ACTION_VOICE_QUALITY_PROBLEM],
        )

    def test_public_support_voice_quality_problem_seed_remains_discovered_after_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.voice.quality"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("voice_quality_problem", seed["action_id"])
        self.assertEqual("Podczas rozmów zanika głos.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual(EVIDENCE_TASK, seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

    def test_live_evidence_persists_verified_root_reprompt_edge_only(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.voice_quality_problem"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("voice_quality_problem", edge["action_id"])
        self.assertEqual("Podczas rozmów zanika głos.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual(EVIDENCE_TASK, edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])


if __name__ == "__main__":
    unittest.main()
