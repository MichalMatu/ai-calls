package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolEvent

internal enum class ConversationRuntimeMode { OWNED_TASK, SUPERVISED_ATTACH, DEVELOPER_RELAY }
internal enum class ConversationOutcomeBoundary { TRANSCRIPT, PLAYBACK_COMPLETE }
internal sealed interface ConversationOutcome {
    data object Completed : ConversationOutcome
    data class Failed(val code: String) : ConversationOutcome
}
internal data class ConversationRunResult(
    val termination: ConversationTermination,
    val report: String,
    val evidenceAwaitingTool: Boolean,
    val chatToken: Long,
)
internal fun interface ConversationDecisionSink {
    fun record(turn: Int, actionId: String?, responseSource: String?, classifierWallMs: Long?, localErrorPresent: Boolean)
}
internal data class ConversationRuntimeHooks(
    val authorized: () -> Boolean,
    val mediaGeneration: () -> Long,
    val turn: () -> Int,
    val emit: (String) -> Unit,
    val decisionSink: ConversationDecisionSink,
    val toolEventSink: (ConversationToolEvent) -> Unit,
)
/** Call direction and preparation belong to a scenario; media and Telecom do not. */
internal interface ConversationScenario {
    val id: String
    val preparationId: String
    val direction: ConversationDirection
    val mode: ConversationRuntimeMode
    val responseKind: ConversationChatFeed.ResponseKind
    fun readinessFailure(): String?
    fun open(hooks: ConversationRuntimeHooks): ConversationDialogueSession
}
internal interface ConversationDialogueSession {
    val backend: TextCallAgentBackend
    val approvalPolicy: TextOutputApprovalPolicy
    val openingText: String? get() = null
    /** The bound Call adapter finalizes evidence after Android confirms the dispatched end_call. */
    val awaitToolCompletion: Boolean get() = false
    fun start()
    fun beginTurn()
    fun onTranscript(turn: Int, text: String)
    fun takeOutcome(boundary: ConversationOutcomeBoundary): ConversationOutcome?
    fun turnBudgetOutcome(): ConversationOutcome
    fun recordTurnDiagnostics(turn: Int)
    fun finalDiagnostics(): List<String>
    /** Auxiliary scenario resources only; the speech pipeline owns backend.close(). */
    fun close()
}
