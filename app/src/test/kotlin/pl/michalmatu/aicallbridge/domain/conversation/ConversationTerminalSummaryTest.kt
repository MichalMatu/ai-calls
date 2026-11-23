package pl.michalmatu.aicallbridge.domain.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination

class ConversationTerminalSummaryTest {
    @Test fun completedHasExactEvidenceOrderButNoFailureReason() {
        val lines = ConversationTerminalSummary.entries(
            ConversationTermination.Completed, 2, 120L, 7L, 70L, "conversation_completed")
        assertEquals(listOf(
            "turns_completed=2",
            "telephony_rx_pcm_bytes=120",
            "telephony_rx_partial_pcm_bytes=7",
            "telephony_tx_pcm_bytes=70",
            "media_rx_tx_observed=true",
            "conversation_terminal_category=completed",
            "chat_relay_live_call_success=true",
            "conversation_end_reason=conversation_completed",
        ), lines)
    }

    @Test fun stoppedDoesNotCountAsSuccessfulConversationOrProofOfTransport() {
        val terminal = ConversationTermination.Stopped(ConversationStopReason.OPERATOR_STOP)
        val lines = ConversationTerminalSummary.entries(
            terminal, 0, 3L, 3L, 0L, terminal.code)
        assertTrue("media_rx_tx_observed=false" in lines)
        assertTrue("conversation_terminal_category=stopped" in lines)
        assertTrue("chat_relay_live_call_success=false" in lines)
        assertFalse(lines.any { it.startsWith("failure_reason=") })
    }

    @Test fun failureReportsSanitizedErrorOnlyAsTechnicalEvidence() {
        val terminal = ConversationTermination.Failed("native_failed")
        val lines = ConversationTerminalSummary.entries(
            terminal, 3, 8L, 0L, 0L, "redacted")
        assertTrue("conversation_terminal_category=failed" in lines)
        assertTrue("media_rx_tx_observed=false" in lines)
        assertEquals("failure_reason=redacted", lines.last())
        assertTrue("conversation_end_reason=redacted" in lines)
        assertFalse(lines.any { it.contains("native_failed") })
    }
}
