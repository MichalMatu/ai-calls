package pl.michalmatu.aicallbridge.conversationtools

enum class ConversationToolId(val wireName: String, val title: String, val description: String) {
    END_CALL("end_call", "Zakończ obsługiwaną rozmowę", "Po pożegnaniu zakończ bieżące połączenie obsługiwane przez AI."),
}

enum class ConversationToolResult { REQUESTED, DISABLED, NOT_AUTHORIZED, FAILED }
enum class ConversationToolStage { REQUESTED, VALIDATED, DISPATCHED, REJECTED, FAILED, REUSED, CONFIRMED, UNKNOWN }
data class ConversationToolEvent(val turn: Int, val tool: ConversationToolId, val stage: ConversationToolStage, val result: ConversationToolResult?)

/** This port is bound by the app to one session. The model never supplies a Call or a destination. */
interface ConversationToolPort {
    fun enabled(tool: ConversationToolId): Boolean
    fun authorized(tool: ConversationToolId): Boolean
    /** True means Android accepted the request, not that the remote endpoint has already disconnected. */
    fun requestEndCall(turn: Int): Boolean
}

/** Model-independent execution and observable outcomes; the port retains actual platform ownership. */
class ConversationToolExecutor(private val port: ConversationToolPort) {
    private val results = mutableMapOf<Pair<Int, ConversationToolId>, ConversationToolResult>()
    @Volatile var diagnosticDeliveryFailed: Boolean = false
        private set

    @Synchronized fun execute(turn: Int, tool: ConversationToolId, emit: (ConversationToolEvent) -> Unit): ConversationToolResult {
        require(turn > 0)
        val key = turn to tool
        fun publish(event: ConversationToolEvent) {
            try { emit(event) } catch (_: Exception) { diagnosticDeliveryFailed = true }
        }
        results[key]?.let {
            publish(ConversationToolEvent(turn, tool, ConversationToolStage.REUSED, it))
            return it
        }
        fun event(stage: ConversationToolStage, result: ConversationToolResult? = null) = publish(ConversationToolEvent(turn, tool, stage, result))
        event(ConversationToolStage.REQUESTED)
        val result = try {
            when {
                !port.enabled(tool) -> ConversationToolResult.DISABLED
                !port.authorized(tool) -> ConversationToolResult.NOT_AUTHORIZED
                else -> {
                    event(ConversationToolStage.VALIDATED)
                    // Observers can revoke a capability; check again immediately before dispatch.
                    when {
                        !port.enabled(tool) -> ConversationToolResult.DISABLED
                        !port.authorized(tool) -> ConversationToolResult.NOT_AUTHORIZED
                        port.requestEndCall(turn) -> ConversationToolResult.REQUESTED
                        else -> ConversationToolResult.NOT_AUTHORIZED
                    }
                }
            }
        } catch (_: Exception) { ConversationToolResult.FAILED }
        results[key] = result
        event(when (result) {
            ConversationToolResult.REQUESTED -> ConversationToolStage.DISPATCHED
            ConversationToolResult.FAILED -> ConversationToolStage.FAILED
            ConversationToolResult.DISABLED, ConversationToolResult.NOT_AUTHORIZED -> ConversationToolStage.REJECTED
        }, result)
        return result
    }
}
