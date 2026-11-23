"""Runtime cleanup retains authority; terminal reporting belongs to pure domain."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/kotlin/pl/michalmatu/aicallbridge"


class ConversationTerminalSummaryBoundary(unittest.TestCase):
    def test_terminal_metadata_projection_moves_out_of_android_runtime(self):
        runtime = (ROOT / "conversation/AndroidConversationRuntime.kt").read_text()
        summary = (ROOT / "domain/conversation/ConversationTerminalSummary.kt").read_text()
        self.assertIn("ConversationTerminalSummary.entries(", runtime)
        self.assertIn("sanitize(reason)", runtime)
        self.assertIn("evidenceRecorder?.finish(reason)", runtime)
        self.assertIn("outputEpoch.incrementAndGet()", runtime)
        self.assertNotIn('lines += "conversation_terminal_category="', runtime)
        for key in (
            "turns_completed=", "telephony_rx_pcm_bytes=", "telephony_rx_partial_pcm_bytes=",
            "telephony_tx_pcm_bytes=", "media_rx_tx_observed=", "conversation_terminal_category=",
            "chat_relay_live_call_success=", "conversation_end_reason=", "failure_reason=",
        ):
            self.assertIn(key, summary)
        for forbidden in ("import android.", "import androidx.",
                          "import pl.michalmatu.aicallbridge.ui.",
                          "CallMediaSessionRuntime", "evidenceRecorder", "placeCall("):
            self.assertNotIn(forbidden, summary)


if __name__ == "__main__":
    unittest.main()
