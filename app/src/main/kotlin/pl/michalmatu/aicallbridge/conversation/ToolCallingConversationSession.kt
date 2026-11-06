package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationtools.ConversationToolExecutor
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolResult
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy

/** Tool proposals share the speech pipeline; execution is released only at its playback boundary. */
internal class ToolCallingConversationSession(
    model: TextCallAgentBackend,
    override val approvalPolicy: TextOutputApprovalPolicy,
    private val tools: ConversationToolExecutor,
    private val hooks: ConversationRuntimeHooks,
    private val responseSource: String,
    override val openingText: String?,
) : ConversationDialogueSession {
    private var awaitingTool = false
    override val awaitToolCompletion get() = awaitingTool
    override val backend = ConversationReplyBackend(model) { reply, elapsed, error ->
        hooks.decisionSink.record(hooks.turn(), reply?.tool?.name ?: if (error) null else "SPEAK", responseSource, elapsed, error)
        hooks.emit("turn_${hooks.turn()}_model_reply_ms=$elapsed")
    }
    override fun start() = Unit
    override fun beginTurn() = Unit
    override fun onTranscript(turn: Int, text: String) = Unit
    override fun takeOutcome(boundary: ConversationOutcomeBoundary): ConversationOutcome? {
        if (boundary != ConversationOutcomeBoundary.PLAYBACK_COMPLETE) return null
        val tool = backend.takeTool() ?: return null
        if (!hooks.authorized()) return ConversationOutcome.Failed("conversation_tool_not_authorized")
        return when (tools.execute(hooks.turn(), tool, hooks.toolEventSink)) {
            ConversationToolResult.REQUESTED -> {
                awaitingTool = true
                ConversationOutcome.Completed
            }
            ConversationToolResult.DISABLED -> null
            ConversationToolResult.NOT_AUTHORIZED -> ConversationOutcome.Failed("conversation_tool_not_authorized")
            ConversationToolResult.FAILED -> ConversationOutcome.Failed("conversation_tool_failed")
        }
    }
    override fun turnBudgetOutcome() = ConversationOutcome.Completed
    override fun recordTurnDiagnostics(turn: Int) = Unit
    override fun finalDiagnostics() = listOf("conversation_tool_completion_pending_at_runtime_end=$awaitingTool",
        "conversation_tool_diagnostic_delivery_failed=${tools.diagnosticDeliveryFailed}")
    override fun close() = Unit
}
