from pathlib import Path
import unittest, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
class I18nDockContract(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.panel=(ROOT/"kotlin/pl/michalmatu/aicallbridge/conversation/ConversationChatPanel.kt").read_text()
  cls.keys=[]
  for lang in ("values","values-en"):
   doc=ET.parse(ROOT/"res"/lang/"strings.xml")
   cls.keys.append({t.attrib["name"]:t.text for t in doc.getroot()})
 def test_resource_parity(self):
  self.assertEqual(set(self.keys[0]),set(self.keys[1]))
  self.assertGreaterEqual(len(self.keys[0]),30)
  self.assertTrue(all(v for table in self.keys for v in table.values()))
 def test_light_surface_and_toggle(self):
  for term in ("UiDesign.surfaceDrawable","live-chat-dock","live-chat-toggle","dp(48)","ImageView.ScaleType.CENTER_INSIDE","live_chat_title","ic_aicalls_chevron_down"):
   self.assertIn(term,self.panel)
  self.assertNotIn("setBackgroundColor(Color.rgb(14, 23, 39))",self.panel)
  self.assertNotIn("ConversationHistoryActivity::class.java", self.panel)
 def test_live_history_and_private_view_stay(self):
  for term in ("ConversationChatFeed.clearVisibleHistory()","conversationMessageView(","if (!isAttachedToWindow) return"):
   self.assertIn(term,self.panel)
if __name__=="__main__": unittest.main()
