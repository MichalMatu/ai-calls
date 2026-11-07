"""Read-only chat audio; do not change the call engine or leak private WAVs."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ArchivedChatAudioUiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.history = (BASE / "conversation/ConversationHistoryActivity.kt").read_text()
        cls.reader = (BASE / "conversation/RecordedConversationHistory.kt").read_text()
        cls.live = (BASE / "conversation/ConversationChatPanel.kt").read_text()
        cls.player = (BASE / "conversation/ArchivedConversationPlayer.kt").read_text()

    def test_fragment_player_requires_phone_idle_and_safe_private_path(self):
        for expected in ("onPause()", "session.audioFor(line)", "audioPlayer.play(id, fileName)"):
            self.assertIn(expected, self.history)
        for expected in ("READ_PHONE_STATE", "telecom.isInCall",
                         "archive.resolveAudioFile", "setDataSource(file.absolutePath)"):
            self.assertIn(expected, self.player)
        self.assertNotIn("MediaPlayer()", self.history)
        self.assertNotIn("setDataSource(", self.history)
        self.assertIn("canonicalFile", self.reader)
        self.assertIn('Regex("turn-[0-9]+-(rx|tx|tx-ack)[.]wav")', self.reader)

    def test_no_call_or_grant_operations_in_history(self):
        self.assertIn("ConversationChatFeed.clearVisibleHistory()", self.live)
        self.assertNotIn("ConversationHistoryActivity::class.java", self.live)
        self.assertIn('tag = "live-chat-dock"', self.live)
        self.assertNotIn("CALL_PHONE", self.history)
        self.assertNotIn("Intent.ACTION_SEND", self.history)
        self.assertNotIn("startForegroundService", self.history)


if __name__ == "__main__":
    unittest.main()
