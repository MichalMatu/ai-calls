"""History details back stays inside Calls, and deleting archives uses a constrained store."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/kotlin/pl/michalmatu/aicallbridge"
class HistoryNavDeletionContract(unittest.TestCase):
    def test_activity_back_and_confirmation(self):
        history=(ROOT/"conversation/ConversationHistoryActivity.kt").read_text()
        chrome=(ROOT/"ui/UiChromeActivity.kt").read_text()
        for key in ("override fun consumeChromeBack()", "OnBackInvokedDispatcher.PRIORITY_DEFAULT",
                    "history-back-to-list", "history-delete:", "AlertDialog.Builder(this)",
                    "history.deleteCompleted(id)"):
            self.assertIn(key,history)
        self.assertIn("fun navigateBack()",chrome)
        self.assertIn("if (!consumeChromeBack()) finish()",chrome)
    def test_store_never_deletes_active_or_external_data(self):
        s=(ROOT/"conversation/RecordedConversationHistory.kt").read_text()
        for key in ("File(directory, \"COMPLETE\").isFile",
                    "directory.parentFile == base", "load(id).complete",
                    "java.nio.file.Files.walkFileTree", "isSymbolicLink"):
            self.assertIn(key,s)
if __name__=="__main__":unittest.main()
