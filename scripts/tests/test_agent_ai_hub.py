"""Agent AI hub final UX contract: no UI-thread Keystore work, safe summaries and PL/EN."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2] / "app/src/main"
SOURCE = ROOT / "kotlin/pl/michalmatu/aicallbridge/ui/AgentAiHubActivity.kt"


class AgentAiHubContractTest(unittest.TestCase):
    def test_five_destinations_and_compact_page_without_duplicate_title(self):
        source = SOURCE.read_text()
        for entry in (
            'destination("skills", R.string.ux07_agent_skills,',
            'destination("scenarios", R.string.ux07_agent_scenarios,',
            'destination("profiles", R.string.ux07_agent_preparation,',
            'destination("vault", R.string.ux07_agent_vault,',
            'destination("tools", R.string.ux07_agent_tools,',
        ):
            self.assertIn(entry, source)
        self.assertNotIn("body.addView(UiDesign.heading(this, getString(R.string.ux07_agent_title))", source)
        self.assertIn("AppNavigation.install(this", source)
        self.assertIn("AppTab.AGENT", source)

    def test_encrypted_reads_are_asynchronous_and_lifecycle_guarded(self):
        source = SOURCE.read_text()
        self.assertIn("Executors.newSingleThreadExecutor()", source)
        self.assertIn("worker.execute {", source)
        self.assertIn("private fun readSnapshot(): Snapshot", source)
        self.assertIn("val snapshot = readSnapshot()", source)
        self.assertIn("main.post {", source)
        self.assertIn("if (token != generation || isFinishing || isDestroyed)", source)
        self.assertIn("worker.shutdown()", source)
        self.assertNotIn("refreshReadOnly()\n    }\n\n    override fun onResume()", source)
        self.assertNotIn("FLAG_SECURE", source)
        self.assertIn("val store = runCatching { AndroidConversationPreparationStore(applicationContext) }", source)
        self.assertIn("val vault = runCatching {", source)

    def test_summaries_are_counts_only_for_private_profile_and_vault(self):
        source = SOURCE.read_text()
        self.assertIn("dataFields.size", source)
        self.assertIn("availableFields().getOrThrow().size", source)
        self.assertIn("R.string.ux07_agent_profile_count", source)
        self.assertIn("R.string.ux07_agent_vault_count", source)
        self.assertNotIn("namedScope(", source)
        self.assertNotIn("fieldIds.joinToString", source)
        self.assertIn("destinations[key]?.contentDescription = titles[key] +", source)

    def test_pl_en_statuses_and_warning_are_precise_and_symmetric(self):
        keys = (
            "ux07_agent_loading", "ux07_agent_profile_count",
            "ux07_agent_vault_count", "ux07_agent_load_error",
        )
        values = []
        for locale in ("values", "values-en"):
            root = ET.parse(ROOT / "res" / locale / "ux07_strings.xml").getroot()
            names = {item.attrib["name"]: (item.text or "") for item in root.findall("string")}
            for name in keys:
                self.assertIn(name, names, f"{locale}:{name}")
            self.assertIn("%1$d", names["ux07_agent_profile_count"])
            self.assertIn("%2$d", names["ux07_agent_profile_count"])
            self.assertIn("%1$d", names["ux07_agent_vault_count"])
            extra = ET.parse(ROOT / "res" / locale / "ux09_ui.xml").getroot()
            warning = {x.attrib["name"]: x.text for x in extra.findall("string")}
            self.assertIn("ux09_agent_scope_warning", warning)
            values.append(warning["ux09_agent_scope_warning"])
        self.assertTrue(all(v and len(v) > 20 for v in values))


if __name__ == "__main__":
    unittest.main()
