import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

class OutgoingWizardFastflowTest(unittest.TestCase):
    def test_custom_first_in_existing_wizard(self):
        wizard = (ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/PhoneTaskWizardActivity.kt").read_text()
        draft = (ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/PhoneTaskDraft.kt").read_text()
        self.assertIn("listOf(ScenarioCatalog.custom) + ScenarioCatalog.templates", wizard)
        self.assertIn('val scenarioId: String = "custom"', draft)
        self.assertIn('if (draft.step == 3) "task-approve" else "task-next"', wizard)
    def test_expired_default_times_auto_refresh(self):
        wizard = (ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/PhoneTaskWizardActivity.kt").read_text()
        self.assertNotIn('tag = "task-refresh-window"', wizard)
        self.assertIn('if (PhoneTaskScheduleUiPolicy.expired(current.scheduled, startTime, endTime, now))', wizard)
        self.assertIn('PhoneTaskScheduleUiPolicy.refreshDefaults', wizard)
        self.assertIn('require(!draft.scheduled || start > now)', wizard)
        self.assertIn('requireNotNull(draft.subscriptionId)', wizard)
    def test_labels_bilingual(self):
        for locale in ("values","values-en"):
            strings = (ROOT / "app/src/main/res" / locale / "strings.xml").read_text()
            for key in ("task_custom_help","task_window_expired"):
                self.assertEqual(1, strings.count('name="'+key+'"'))

if __name__ == "__main__": unittest.main()
