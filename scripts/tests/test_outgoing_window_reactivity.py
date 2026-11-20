from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WIZARD = ROOT / "app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/PhoneTaskWizardActivity.kt"

class OutgoingWindowReactivityTest(unittest.TestCase):
    def test_start_selection_never_silently_changes_end(self):
        source = WIZARD.read_text()
        select_start = source.split('val start = dateButton(R.string.task_start_time', 1)[1].split('}.apply { tag = "task-start-time"', 1)[0]
        self.assertIn('draft = capture().copy(startLocal = it)', select_start)
        self.assertNotIn('endLocal =', select_start)

    def test_checkbox_refreshes_notice_without_full_screen_render(self):
        source = WIZARD.read_text()
        listener = source.split('later.setOnCheckedChangeListener { _, checked ->', 1)[1].split('retry.setOnCheckedChangeListener', 1)[0]
        self.assertIn('draft = capture()', listener)
        self.assertIn('updateWindowNotices()', listener)
        self.assertNotIn('render()', listener)
        self.assertIn('tag = "task-window-notices"', source)
        self.assertIn('R.string.task_window_order_invalid', source)

if __name__ == "__main__": unittest.main()
