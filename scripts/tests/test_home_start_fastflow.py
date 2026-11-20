"""Source contracts for safe, privacy-preserving user Home fast-flow."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CODE = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge"

class HomeFastFlowContract(unittest.TestCase):
    def test_notes_tile_opens_notebook_without_configuration_or_call(self):
        home = (CODE / "MainActivity.kt").read_text()
        tools = (CODE / "tools/ConversationToolsActivity.kt").read_text()
        self.assertIn('tag = "home-notebook"', home)
        self.assertIn("EXTRA_NOTEBOOK_ONLY, true", home)
        self.assertIn("if (!notebookOnly)", tools)
        self.assertIn('tag = "tools-notebook-list"', tools)
        self.assertIn("notebooks().size", home)
        self.assertNotIn("placeCall(", home)
        self.assertNotIn("ACTION_CALL", home)

    def test_stop_only_shown_when_runtime_reports_activity(self):
        home = (CODE / "MainActivity.kt").read_text()
        status = (CODE / "ui/HomeActiveStatus.kt").read_text()
        self.assertIn('tag = "home-stop-ai"; visibility = View.GONE', home)
        self.assertIn("state.showStop", home)
        self.assertIn("ConversationChatFeed.snapshot().active", home)
        self.assertIn("CampaignLivePhase.INACTIVE", status)
        self.assertIn("HomeActiveState(R.string.home_activity_idle, false)", status)
        self.assertIn("AppOwnedCampaignService.ACTION_STOP_ALL_AI", home)

    def test_no_personal_notes_or_phone_numbers_on_home(self):
        home = (CODE / "MainActivity.kt").read_text()
        self.assertIn("resources.getQuantityString(", home)
        self.assertIn("R.plurals.home_notebook_count", home)
        self.assertIn("R.plurals.home_task_count", home)
        self.assertNotIn("note.text", home)
        self.assertNotIn("recipient.number", home)
        self.assertIn("PhoneTaskWizardActivity::class.java", home)

    def test_locales_include_labels_and_plural_counts(self):
        for language in ("values", "values-en"):
            text = (ROOT/"app/src/main/res"/language/"strings.xml").read_text()
            for key in ("home_notebook_shortcut", "home_activity_idle",
                        "home_activity_incoming", "home_outgoing_hint",
                        "home_task_count", "home_notebook_count"):
                self.assertIn('name="'+key+'"', text)

if __name__ == "__main__": unittest.main()
