"""No all-caps wall; preserve exact START/STOP and accessible contrast on S22."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class MenuVisualSourceContract(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.main=(ROOT/"MainActivity.kt").read_text()
  cls.section=(ROOT/"UiAccordionSection.kt").read_text()
  cls.chat=(ROOT/"conversation/ConversationChatPanel.kt").read_text()
 def test_safe_routes(self):
  for x in ("addView(startAuthorizedTaskButton)","addView(stopAiButton)","addView(conversationHistoryButton)","addView(labShortcutButton)","addControl(oneTapButton)"):
   self.assertIn(x,self.main)
 def test_contrast_and_compactness(self):
  for x in ('setColor(Color.WHITE)','setTextColor(ink)','Color.rgb(245, 248, 252)'):
   self.assertIn(x,self.section)
  for x in ('settings-audio-mode-spinner','settings-provider-spinner','WindowInsets.Type.systemBars()','main-screen-root','initiallyExpanded = false'):
   self.assertIn(x,self.main)
  for x in ('live-chat-scroll','live-chat-toggle','clearButton.visibility'):
   self.assertIn(x,self.chat)
if __name__=="__main__": unittest.main()
