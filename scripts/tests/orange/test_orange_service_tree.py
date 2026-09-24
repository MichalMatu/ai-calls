import json
import unittest
from pathlib import Path


ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "settings.gradle.kts").is_file())
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
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-explorer-live-v115-20260921", edge["evidence_task"])

    def test_v141_exact_kosci_orange_preroll_is_durable_physical_evidence(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        observations = {item["transcript"]: item for item in tree["root_acquisition_observations"]}
        item = observations["kości orange"]
        self.assertEqual("VERIFIED", item["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", item["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", item["effect"])
        self.assertEqual("chatgpt-orange-internet-live-retry-v141-20260922", item["evidence_task"])

    def test_v167_exact_wielkosci_orange_preroll_is_durable_physical_evidence(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        observations = {item["transcript"]: item for item in tree["root_acquisition_observations"]}
        item = observations["wielkości orange"]
        self.assertEqual("VERIFIED", item["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", item["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", item["effect"])
        self.assertEqual("chatgpt-orange-roaming-prices-live-v167-20260922", item["evidence_task"])

    def test_v172_exact_g_jakosci_orange_preroll_is_durable_physical_evidence(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        observations = {item["transcript"]: item for item in tree["root_acquisition_observations"]}
        item = observations["g jakości orange"]
        self.assertEqual("VERIFIED", item["status"])
        self.assertEqual("IGNORABLE_PREROLL_FRAGMENT", item["kind"])
        self.assertEqual("BOUNDED_RETRY_ALLOWED", item["effect"])
        self.assertEqual("chatgpt-orange-roaming-prices-live-retry-v172-20260922", item["evidence_task"])

    def test_v145_internet_problem_reprompt_edge_is_durable_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.internet_problem"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("internet_problem", edge["action_id"])
        self.assertEqual("Mam problem z internetem.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-internet-live-v145-20260922", edge["evidence_task"])
        self.assertFalse(edge["service_route_verified"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.internet.problem"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("internet_problem", seed["action_id"])
        self.assertEqual("Mam problem z internetem.", seed["speech"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

    def test_v123_invoice_attempt_is_discovered_but_service_route_is_not_claimed_verified(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.invoice_status"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("invoice_status", edge["action_id"])
        self.assertEqual("Chcę sprawdzić fakturę.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-invoice-live-v123-20260921", edge["evidence_task"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        invoice = seeds["orange.invoice.status"]
        self.assertEqual("DISCOVERED", invoice["status"])
        self.assertEqual("invoice_status", invoice["action_id"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", invoice["risk"])
        self.assertEqual("Chcę sprawdzić fakturę.", invoice["speech"])
        self.assertEqual("REPROMPT", invoice["last_outcome"])
        self.assertIn("service_route_not_verified", invoice["next_evidence"])

    def test_v136_simpler_invoice_topic_is_separate_verified_reprompt_evidence(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}

        original = edges["orange.root.invoice_status"]
        self.assertEqual("chatgpt-orange-invoice-live-v123-20260921", original["evidence_task"])

        edge = edges["orange.root.invoice_topic"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("invoice_topic", edge["action_id"])
        self.assertEqual("Faktura.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-invoice-topic-live-installed-v136-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

    def test_v151_roaming_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.roaming_info"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("roaming_info", edge["action_id"])
        self.assertEqual("Roaming.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-roaming-live-v151-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.roaming.info"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("roaming_info", seed["action_id"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

    def test_v176_roaming_prices_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.roaming_prices"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("roaming_prices", edge["action_id"])
        self.assertEqual("Chcę sprawdzić ceny w roamingu.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-roaming-prices-live-final-v176-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        self.assertEqual("DISCOVERED", seeds["orange.roaming.info"]["status"])

    def test_v184_outage_reprompt_edge_is_verified_without_claiming_service_route(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        edges = {edge["id"]: edge for edge in tree["edges"]}
        edge = edges["orange.root.outage_topic"]
        self.assertEqual("orange.root", edge["from"])
        self.assertEqual("orange.root.reprompt", edge["to"])
        self.assertEqual("outage_topic", edge["action_id"])
        self.assertEqual("Awaria.", edge["speech"])
        self.assertEqual("VERIFIED", edge["status"])
        self.assertEqual("READ_ONLY", edge["risk"])
        self.assertEqual("REPROMPT", edge["observed_outcome"])
        self.assertEqual("chatgpt-orange-outage-live-v184-20260922", edge["evidence_task"])
        self.assertEqual("max_duration", edge["observation_endpoint_reason"])
        self.assertFalse(edge["service_route_verified"])

        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.outage.info"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("outage_topic", seed["action_id"])
        self.assertEqual("Awaria.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-outage-live-v184-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])

    def test_v196_wifi_seed_remains_discovered_after_physical_reprompt(self):
        tree = json.loads(TREE_PATH.read_text(encoding="utf-8"))
        seeds = {seed["service_id"]: seed for seed in tree["seeds"]}
        seed = seeds["orange.wifi.problem"]
        self.assertEqual("DISCOVERED", seed["status"])
        self.assertEqual("wifi_problem", seed["action_id"])
        self.assertEqual("Mam problem z Wi-Fi.", seed["speech"])
        self.assertEqual("AUTH_REQUIRED_POSSIBLE", seed["risk"])
        self.assertEqual("operator_public_support_backlog", seed["source"])
        self.assertEqual("chatgpt-orange-wifi-live-retry-v196-20260922", seed["last_evidence"])
        self.assertEqual("REPROMPT", seed["last_outcome"])
        self.assertIn("service_route_not_verified", seed["next_evidence"])


if __name__ == "__main__":
    unittest.main()
