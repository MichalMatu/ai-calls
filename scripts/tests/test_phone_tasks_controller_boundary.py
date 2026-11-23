"""Phone tasks Activity renders pure grouped state and copies drafts through a presenter."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class PhoneTasksControllerBoundary(unittest.TestCase):
    def test_list_and_draft_projection_are_outside_activity(self):
        owner = (ROOT / "campaign/PhoneTasksActivity.kt").read_text()
        controller = (ROOT / "presentation/tasks/PhoneTaskListController.kt").read_text()
        for required in ("PhoneTaskListController.sections(tasks, System.currentTimeMillis())",
                         "section.tasks.forEach { value ->",
                         "PhoneTaskListController.duplicateDraft(task)"):
            self.assertIn(required, owner)
        for required in ("PhoneTaskManagement.Group.entries", "sortedByDescending { it.approvedAt }",
                         "PhoneTaskDraft(", 'joinToString("\\n")',
                         "recordAudio = task.recordAudio"):
            self.assertIn(required, controller)
        for forbidden in ("import android.", "import androidx.", "import pl.michalmatu.aicallbridge.ui.",
                          "startActivity(", "startService(", "placeCall(",
                          "getSharedPreferences("):
            self.assertNotIn(forbidden, controller)


if __name__ == "__main__":
    unittest.main()
