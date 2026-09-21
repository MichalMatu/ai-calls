import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TREE_PATH = ROOT / "service-packs" / "orange" / "service_tree.v1.json"


class OrangeServiceTreeTest(unittest.TestCase):
    def test_v115_root_and_list_capabilities_self_loop_are_durable_evidence(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        self.assertEqual(1, tree["version"])
        self.assertEqual("orange", tree["service"])
        self.assertEqual("510100100", tree["target"])

        nodes = {node["id"]: node for node in tree["nodes"]}
        root = nodes["orange.root"]
        self.assertEqual("VERIFIED", root["status"])
        self.assertEqual("VOICE_INTENT_ROUTER", root["kind"])
        self.assertIn("powiedz w jakiej sprawie dzwonisz", root["observed_prompt"])

        reprompt = nodes["orange.root.reprompt"]
        self.assertEqual("VERIFIED", reprompt["status"])
        self.assertIn("powiedz proszę czego dotyczy twoja sprawa", reprompt["observed_prompt"])

        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.list_capabilities"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("list_capabilities", edge["action_id"])
        self.assertEqual("Jakie sprawy możesz załatwić?", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("chatgpt-orange-explorer-live-v115-20260921", edge["evidence_task"])

    def test_invoice_status_seed_is_not_claimed_verified_before_live_evidence(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        invoice = seeds["orange.invoice.status"]
        self.assertEqual("SEED", invoice["status"])
        self.assertEqual("invoice_status", invoice["action_id"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", invoice["risk"])
        self.assertEqual("Chcę sprawdzić fakturę.", invoice["speech"])


if __name__ == "__main__":
    unittest.main()
