package pl.michalmatu.aicallbridge.conversationcore

enum class ConversationStopReason(val code: String) {
    CALL_ENDED("owned_call_ended"),
    HUMAN_TAKEOVER("human_takeover"),
    OPERATOR_STOP("operator_stopped"),
    POLICY_REVOKED("conversation_authority_revoked"),
    CALL_HELD("call_held"),
    OWNERSHIP_CHANGED("call_ownership_changed"),
    SERVICE_DESTROYED("conversation_service_destroyed"),
    SESSION_CANCELLED("conversation_session_cancelled"),
}

/** Ending a dialogue is separate from proving any external task result. */
sealed interface ConversationTermination {
    data object Completed : ConversationTermination
    data class Stopped(val reason: ConversationStopReason) : ConversationTermination
    data class Failed(override val code: String) : ConversationTermination

    val code: String get() = when (this) {
        Completed -> "conversation_completed"
        is Stopped -> reason.code
        is Failed -> code
    }
}
