package pl.michalmatu.aicallbridge.conversationtools

enum class ConversationToolId(val wireName: String, val title: String, val description: String) {
    END_CALL("end_call", "Zakończ obsługiwaną rozmowę", "Po pożegnaniu zakończ bieżące połączenie obsługiwane przez AI."),
    SAVE_NOTE("save_note", "Zapisz ustalenie", "Dodaj prywatną notatkę lub propozycję działania."),
}

/** Future action categories are inert notebook entries, never external operations. */
enum class ConversationNoteKind { NOTE, TASK, CALENDAR_PROPOSAL, MESSAGE_DRAFT }

data class ConversationNoteProposal(val kind: ConversationNoteKind, val content: String) {
    init {
        require(content.isNotBlank() && content.length <= 2_000 &&
            content.none { Character.isISOControl(it) && it != '\n' && it != '\t' }) {
            "invalid_notebook_proposal"
        }
    }
    override fun toString() = "ConversationNoteProposal(REDACTED, kind=$kind)"
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
    /** Synchronous, durable, session-scoped write. No network or external side effect. */
    fun saveNote(turn: Int, proposal: ConversationNoteProposal): Boolean = false
    /** Write one evolving note for the current authorized call; never changes another call. */
    fun saveAutomaticNote(turn: Int, proposal: ConversationNoteProposal): Boolean = false
}

/** Model-independent execution and observable outcomes; the port retains actual platform ownership. */
class ConversationToolExecutor(private val port: ConversationToolPort) {
    private val results = mutableMapOf<Pair<Int, ConversationToolId>, ConversationToolResult>()
    private val automaticResults = mutableMapOf<Int, Pair<ConversationNoteProposal, ConversationToolResult>>()
    @Volatile var diagnosticDeliveryFailed: Boolean = false
        private set

    fun execute(turn: Int, tool: ConversationToolId,
        emit: (ConversationToolEvent) -> Unit): ConversationToolResult =
        execute(turn, tool, null, emit)

    @Synchronized fun execute(turn: Int, tool: ConversationToolId,
        proposal: ConversationNoteProposal?, emit: (ConversationToolEvent) -> Unit): ConversationToolResult =
        executeScoped(turn, tool, proposal, emit, appIdleClosure = false)

    /** Model-independent, privacy-gated, per-call upsert. May accompany END_CALL.
     * Emitted events use the established SAVE_NOTE identity without call-control authority.
     */
    @Synchronized fun saveAutomaticNote(turn: Int, proposal: ConversationNoteProposal,
        emit: (ConversationToolEvent) -> Unit): ConversationToolResult {
        require(turn > 0)
        fun publish(stage: ConversationToolStage, result: ConversationToolResult? = null) {
            try {
                emit(ConversationToolEvent(turn, ConversationToolId.SAVE_NOTE, stage, result))
            } catch (_: Exception) { diagnosticDeliveryFailed = true }
        }
        automaticResults[turn]?.let { (previous, result) ->
            if (previous != proposal) return ConversationToolResult.FAILED
            publish(ConversationToolStage.REUSED, result)
            return result
        }
        publish(ConversationToolStage.REQUESTED)
        val result = try {
            when {
                !port.enabled(ConversationToolId.SAVE_NOTE) -> ConversationToolResult.DISABLED
                !port.authorized(ConversationToolId.SAVE_NOTE) -> ConversationToolResult.NOT_AUTHORIZED
                else -> {
                    publish(ConversationToolStage.VALIDATED)
                    when {
                        !port.enabled(ConversationToolId.SAVE_NOTE) -> ConversationToolResult.DISABLED
                        !port.authorized(ConversationToolId.SAVE_NOTE) -> ConversationToolResult.NOT_AUTHORIZED
                        port.saveAutomaticNote(turn, proposal) -> ConversationToolResult.REQUESTED
                        else -> ConversationToolResult.FAILED
                    }
                }
            }
        } catch (_: Exception) { ConversationToolResult.FAILED }
        automaticResults[turn] = proposal to result
        publish(when (result) {
            ConversationToolResult.REQUESTED -> ConversationToolStage.DISPATCHED
            ConversationToolResult.FAILED -> ConversationToolStage.FAILED
            else -> ConversationToolStage.REJECTED
        }, result)
        if (result == ConversationToolResult.REQUESTED)
            publish(ConversationToolStage.CONFIRMED, result)
        return result
    }

    /** Only the app runtime can arm idle closure; the exact authorized Call must still own it. */
    @Synchronized fun executeIdleClose(turn: Int,
        emit: (ConversationToolEvent) -> Unit): ConversationToolResult =
        executeScoped(turn, ConversationToolId.END_CALL, null, emit, appIdleClosure = true)

    private fun executeScoped(turn: Int, tool: ConversationToolId,
        proposal: ConversationNoteProposal?, emit: (ConversationToolEvent) -> Unit,
        appIdleClosure: Boolean): ConversationToolResult {
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
                !appIdleClosure && !port.enabled(tool) -> ConversationToolResult.DISABLED
                !port.authorized(tool) -> ConversationToolResult.NOT_AUTHORIZED
                else -> {
                    event(ConversationToolStage.VALIDATED)
                    // Observers can revoke a capability; check again immediately before dispatch.
                    when {
                        !appIdleClosure && !port.enabled(tool) -> ConversationToolResult.DISABLED
                        !port.authorized(tool) -> ConversationToolResult.NOT_AUTHORIZED
                        tool == ConversationToolId.END_CALL && port.requestEndCall(turn) ->
                            ConversationToolResult.REQUESTED
                        tool == ConversationToolId.SAVE_NOTE && proposal != null &&
                            port.saveNote(turn, proposal) -> ConversationToolResult.REQUESTED
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
        if (result == ConversationToolResult.REQUESTED && tool == ConversationToolId.SAVE_NOTE)
            event(ConversationToolStage.CONFIRMED, result) // synchronous encrypted local write
        return result
    }
}
