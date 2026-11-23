"""Minimal actions and reversible skip must be wired to the same exact recipient."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign"
UI = (BASE / "PhoneTasksActivity.kt").read_text()
POLICY = (BASE / "PhoneTaskManagement.kt").read_text()

class CompactTaskDetailsTest(unittest.TestCase):
    def test_primary_actions_are_inline_and_secondary_is_in_one_more_menu(self):
        self.assertEqual(1, UI.count('tag = "task-options"'))
        self.assertIn('primaryActions = LinearLayout', UI)
        self.assertIn('R.string.task_compare to { showComparison(task) }', UI)
        self.assertNotIn('tag = "task-compare"', UI)
        self.assertIn('PhoneTaskManagement.hasComparison(task)', UI)
        self.assertIn('tag = "task-recipient-details:" + recipient.id', UI)
        self.assertIn('tag = "task-details-toggle:" + recipient.id', UI)
        self.assertIn('expandedRecipients = mutableSetOf<String>()', UI)
        self.assertIn('recipient.id in expandedRecipients', UI)
        self.assertIn('expandedRecipients.add(recipient.id)', UI)
        self.assertIn('expandedRecipients.remove(recipient.id)', UI)

    def test_skip_and_restore_actions_share_the_compact_row(self):
        self.assertIn('tag = "task-skip:" + recipient.id', UI)
        self.assertIn('tag = "task-restore:" + recipient.id', UI)
        self.assertIn('PhoneTaskManagement.skipQueued(current, recipient.id)', UI)
        self.assertIn('PhoneTaskManagement.restoreSkipped(current, recipient.id', UI)
        self.assertIn('setPositiveButton(R.string.task_restore)', UI)
        self.assertIn('PhoneTaskState.FINISHED) PhoneTaskState.PAUSED', POLICY)

    def test_bilingual_action_labels(self):
        for name in ("values", "values-pl"):
            source = (ROOT / "app/src/main/res" / name / "strings.xml").read_text()
            for key in ("task_skip_short", "task_restore", "task_restore_warning",
                        "task_restore_finished_warning", "task_show_details",
                        "task_hide_details", "task_pause_short", "task_resume_short", "task_cancel_short"):
                self.assertIn('name="'+key+'"', source)

if __name__ == "__main__":
    unittest.main()
