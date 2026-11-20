"""History details back stays inside Calls, and deleting archives uses a constrained store."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class HistoryNavDeletionContract(unittest.TestCase):
    def test_activity_back_and_confirmation(self):
        history=(ROOT/"conversation/ConversationHistoryActivity.kt").read_text()
        chrome=(ROOT/"ui/UiChromeActivity.kt").read_text()
        for key in ("override fun consumeChromeBack()", "OnBackPressedCallback", "onBackPressedDispatcher.addCallback(this, detailBack)",
                    "history-detail", "history-open:", "history-delete:", "AlertDialog.Builder(this)",
                    "controller.deleteCompletedConfirmed(id, callback)", "controller.deleteAbandonedConfirmed(id, callback)"):
            self.assertIn(key,history)
        self.assertNotIn('tag = "history-back-to-list"',history)
        self.assertIn("fun navigateBack()",chrome)
        self.assertIn("if (!consumeChromeBack()) finish()",chrome)
    def test_compact_detail_and_complete_chronological_extra_clips(self):
        history=(ROOT/"conversation/ConversationHistoryActivity.kt").read_text()
        archive=(ROOT/"conversation/RecordedConversationHistory.kt").read_text()
        self.assertNotIn('R.string.history_end_reason, code',history)
        self.assertNotIn('R.string.history_audio_count, session.audioFiles.size',history)
        self.assertIn('visibility = View.GONE',history)
        self.assertIn('showPlaybackStatus(',history)
        self.assertLess(history.index('content.addView(allClips,'),
                        history.index('content.addView(playbackStatus,'))
        self.assertIn('session.extraAudioClips()',history)
        self.assertIn('audioTimeline.filterNot { it in attached }',archive)
        self.assertIn('compareByDescending<RecordedConversation> { it.startedAtEpochMs }',archive)

    def test_store_never_deletes_active_or_external_data(self):
        s=(ROOT/"conversation/RecordedConversationHistory.kt").read_text()
        for key in ("File(directory, \"COMPLETE\").isFile",
                    "directory.parentFile == base", "load(id).complete",
                    "java.nio.file.Files.walkFileTree", "isSymbolicLink"):
            self.assertIn(key,s)
if __name__=="__main__":unittest.main()
