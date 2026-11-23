"""Static contracts for safe user task management."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign"

class TaskManagementContract(unittest.TestCase):
    def test_delete_requires_terminal_state_and_store_revalidation(self):
        policy = (SRC/"PhoneTaskManagement.kt").read_text()
        ui = (SRC/"PhoneTasksActivity.kt").read_text()
        for token in ("PhoneTaskState.CANCELLED", "PhoneTaskState.FINISHED",
                      "PhoneRecipientState.IN_FLIGHT", "it.endedAt == null",
                      "task.id != activeTaskId", "fun removeTerminal"):
            self.assertIn(token, policy)
        self.assertIn('PhoneTaskManagement.removeTerminal(list, task.id, PhoneTaskWork.activeTaskId)', ui)
        self.assertIn('setPositiveButton(R.string.task_delete)', ui)
        self.assertIn('setNegativeButton(android.R.string.cancel', ui)
        self.assertNotIn('deleteNotebook(', ui)
        self.assertNotIn('deleteConversation(', ui)

    def test_grouped_list_and_nonprimary_options(self):
        ui = (SRC/"PhoneTasksActivity.kt").read_text()
        presenter = (ROOT/"app/src/main/kotlin/pl/michalmatu/aicallbridge/presentation/tasks/PhoneTaskListController.kt").read_text()
        self.assertIn('PhoneTaskManagement.Group.entries', presenter)
        self.assertIn('PhoneTaskListController.sections(', ui)
        for token in ('"task-group:"',
                      'tag = "task-options"', 'R.string.task_delete_warning',
                      'PhoneTaskWizardActivity::class.java', 'task_pause', 'task_resume',
                      'task_cancel', 'task_compare'):
            self.assertIn(token, ui)
        self.assertIn('PhoneTaskManagement.Group.SCHEDULED', ui)

    def test_bilingual_labels_and_independent_call_archive(self):
        for locale in ("values", "values-pl"):
            txt=(ROOT/"app/src/main/res"/locale/"strings.xml").read_text()
            for key in ("task_group_current", "task_group_scheduled",
                        "task_group_history", "task_options", "task_delete",
                        "task_delete_title", "task_delete_warning", "task_delete_failed"):
                self.assertIn('name="'+key+'"', txt)

if __name__ == "__main__": unittest.main()
