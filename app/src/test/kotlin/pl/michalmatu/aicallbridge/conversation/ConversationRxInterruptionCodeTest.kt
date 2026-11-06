package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination

class ConversationRxInterruptionCodeTest {
    @Test fun recorderCodesCoverAllTypedReasons() {
        val accepted = setOf("OWNED_CALL_ENDED", "OPERATOR_STOP", "SESSION_END")
        for (reason in ConversationStopReason.entries) {
            val actual = ConversationTermination.Stopped(reason).rxInterruptionCode()
            assertTrue("unsupported recorder cause: $reason → $actual", actual in accepted)
            val expected = when (reason) {
                ConversationStopReason.CALL_ENDED -> "OWNED_CALL_ENDED"
                ConversationStopReason.OPERATOR_STOP -> "OPERATOR_STOP"
                else -> "SESSION_END"
            }
            assertEquals(expected, actual)
        }
    }

    @Test fun completedAndFailedSessionAreSafeForInterruptedRx() {
        assertEquals("SESSION_END", ConversationTermination.Completed.rxInterruptionCode())
        assertEquals("SESSION_END", ConversationTermination.Failed("synthetic_failure").rxInterruptionCode())
    }
}
