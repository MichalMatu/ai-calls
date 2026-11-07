"""Shared back toolbar for child Activities, no change to call runtime."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge/ui"
class ChildToolbarContract(unittest.TestCase):
    def test_chrome_back_not_in_home_shell(self):
        nav=(ROOT/"AppNavigation.kt").read_text()
        self.assertIn('tag = "app-child-toolbar"', nav)
        self.assertIn('tag = "app-child-back"', nav)
        self.assertIn('if (activity is UiChromeActivity) addView(childToolbar(activity, active))', nav)
        self.assertIn('setOnClickListener { activity.finish() }', nav)
        self.assertIn("activity.getString(R.string.ui_back)", nav)
        self.assertIn("UiDesign.dp(activity, 48)", nav)
if __name__=="__main__": unittest.main()
