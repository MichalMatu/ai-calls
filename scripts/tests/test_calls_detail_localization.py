"""Calls detail is localized in Polish and English without changing call engine."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main"
class CallsLocalizationContract(unittest.TestCase):
    def test_history_strings_have_pl_en_values(self):
        history=(ROOT/"kotlin/pl/michalmatu/aicallbridge/conversation/ConversationHistoryActivity.kt").read_text()
        pl={s.attrib["name"]:s.text for s in ET.parse(ROOT/"res/values/strings.xml").getroot().findall("string")}
        en={s.attrib["name"]:s.text for s in ET.parse(ROOT/"res/values-en/strings.xml").getroot().findall("string")}
        keys=sorted(set(__import__("re").findall(r"R.string.(history_\w+)",history)))
        self.assertGreaterEqual(len(keys),20)
        for name in keys:
            with self.subTest(name=name):
                self.assertTrue(pl.get(name))
                self.assertTrue(en.get(name))
        self.assertIn("ArchivedConversationPlayer.StartResult.PLAYING -> R.string.history_playing",history)
        self.assertIn("playbackStatus?.text = getString(textId)",history)
if __name__=="__main__": unittest.main()
