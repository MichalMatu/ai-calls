"""Buttons on existing child pages have a minimum visual gap, without callback migration."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class ActionSpacingContract(unittest.TestCase):
    def test_chrome_reuses_child_buttons_but_adds_gaps(self):
        chrome=(ROOT/"ui/UiChromeActivity.kt").read_text()
        self.assertIn("spaceActionRows(view)",chrome)
        self.assertIn("params.bottomMargin = maxOf(params.bottomMargin, gap)",chrome)
        self.assertIn("params.marginEnd = maxOf(params.marginEnd, gap)",chrome)
        self.assertIn("child !is android.widget.CompoundButton",chrome)
        self.assertIn("OnGlobalLayoutListener",chrome)
        self.assertIn("params.bottomMargin < gap",chrome)
        self.assertIn("params.marginEnd < gap",chrome)
        history=(ROOT/"conversation/ConversationHistoryActivity.kt").read_text()
        self.assertIn('tag = "history-extra-clips"',history)
        self.assertIn("history_more_segments_collapsed",history)
        self.assertIn("history_play_extra_numbered",history)
    def test_home_primary_action_has_a_real_margin(self):
        home=(ROOT/"MainActivity.kt").read_text()
        self.assertIn("startAuthorizedTaskButton.layoutParams",home)
        self.assertIn("bottomMargin = dp(12)",home)
        self.assertIn("oneTapButton.layoutParams",home)
if __name__=="__main__":unittest.main()
