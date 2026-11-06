package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination

/**
 * Keep recorder RX_INTERRUPTED codes compatible with the existing safe allowlist.
 * The more specific typed end reason is retained separately in runtime and END.
 */
internal fun ConversationTermination.rxInterruptionCode(): String = when (this) {
    ConversationTermination.Completed -> "SESSION_END"
    is ConversationTermination.Failed -> "SESSION_END"
    is ConversationTermination.Stopped -> when (reason) {
        ConversationStopReason.CALL_ENDED -> "OWNED_CALL_ENDED"
        ConversationStopReason.OPERATOR_STOP -> "OPERATOR_STOP"
        else -> "SESSION_END"
    }
}
