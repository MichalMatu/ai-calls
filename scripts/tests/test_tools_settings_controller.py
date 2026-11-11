"""Tool capability settings are separate from the UI, with symmetric PL/EN labels."""
from pathlib import Path
import xml.etree.ElementTree as ET
import unittest
ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
K=ROOT/"kotlin/pl/michalmatu/aicallbridge/tools"
class ToolsUiControllerContract(unittest.TestCase):
    def test_separates_storage_and_status_from_views(self):
        activity=(K/"ConversationToolsActivity.kt").read_text()
        controller=(K/"ConversationToolsSettingsController.kt").read_text()
        self.assertIn("ConversationToolsSettingsController(this)",activity)
        self.assertIn("controller.save(",activity)
        self.assertNotIn("controller.latestStatus()",activity)
        self.assertNotIn("tools-latest-status", activity)
        self.assertIn("controller.save(", activity)
        self.assertIn('tag = "tools-notebook-list"', activity)
        self.assertNotIn("ConversationToolSettingsStore(",activity)
        self.assertNotIn("ConversationToolUi.latestText()",activity)
        self.assertIn("ConversationToolSettingsStore(context.applicationContext)",controller)
        self.assertIn("ConversationToolUi.latestEvent()",controller)
        self.assertIn("uiContext.getString(",controller)
    def test_labels_available_both_languages(self):
        for locale in ("values","values-en"):
            resources=ET.parse(ROOT/"res"/locale/"strings.xml").getroot()
            names={e.attrib["name"] for e in resources.findall("string")}
            self.assertTrue(all("tools_ui_"+v in names for v in
                ("title","description","end_call_title","end_call_detail","save","saved","failed","end_hint")))
if __name__=="__main__":unittest.main()
