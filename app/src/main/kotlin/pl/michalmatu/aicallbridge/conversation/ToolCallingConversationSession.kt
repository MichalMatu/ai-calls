package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolExecutor
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolResult
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

/** Tool proposals share the speech pipeline; execution is released only at its playback boundary. */
internal class ToolCallingConversationSession(
    model: TextCallAgentBackend,
    releasePolicy: TextOutputApprovalPolicy,
    private val tools: ConversationToolExecutor,
    private val hooks: ConversationRuntimeHooks,
    private val responseSource: String,
    override val openingText: String?,
) : ConversationDialogueSession {
    private var awaitingTool = false
    // The text controller cancels its model backend after approval, before TX.
    // Save the approved proposal here; never dispatch until playback completion.
    private val approvedTool = AtomicReference<ConversationReply?>(null)
    private val appIdleClosureArmed = AtomicBoolean(false)
    private val approvedAppIdleClose = AtomicBoolean(false)
    override val awaitToolCompletion get() = awaitingTool
    override val backend = ConversationReplyBackend(model) { reply, elapsed, error ->
        hooks.decisionSink.record(hooks.turn(), reply?.tool?.name ?: if (error) null else "SPEAK", responseSource, elapsed, error)
        hooks.emit("turn_${hooks.turn()}_model_reply_ms=$elapsed")
    }
    override val approvalPolicy = TextOutputApprovalPolicy { speech ->
        val decision = releasePolicy.evaluate(speech)
        val proposed = backend.takeProposal()
        val appIdleClose = appIdleClosureArmed.getAndSet(false)
        approvedAppIdleClose.set(decision == TextOutputDecision.RELEASE && appIdleClose)
        approvedTool.set(if (decision == TextOutputDecision.RELEASE) {
            if (appIdleClose) ConversationReply(speech, ConversationToolId.END_CALL)
            else proposed?.takeIf { it.tool != null }
        } else null)
        decision
    }
    override fun start() { runCatching { backend.prewarm() } }
    override fun beginTurn() {
        approvedTool.set(null)
        appIdleClosureArmed.set(false)
        approvedAppIdleClose.set(false)
    }
    override fun armIdleClosure(): Boolean {
        if (!hooks.authorized() || awaitingTool) return false
        appIdleClosureArmed.set(true)
        return true
    }
    override fun onTranscript(turn: Int, text: String) = Unit
    override fun takeOutcome(boundary: ConversationOutcomeBoundary): ConversationOutcome? {
        if (boundary != ConversationOutcomeBoundary.PLAYBACK_COMPLETE) return null
        val proposed = approvedTool.getAndSet(null) ?: return null
        val tool = proposed.tool ?: return null
        if (!hooks.authorized()) return ConversationOutcome.Failed("conversation_tool_not_authorized")
        val result = if (approvedAppIdleClose.getAndSet(false))
            tools.executeIdleClose(hooks.turn(), hooks.toolEventSink)
        else tools.execute(hooks.turn(), tool, proposed.note, hooks.toolEventSink)
        return when (result) {
            ConversationToolResult.REQUESTED -> {
                if (tool == ConversationToolId.END_CALL) {
                    awaitingTool = true
                    ConversationOutcome.Completed
                } else null // SAVE_NOTE is durable, but conversation continues naturally.
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
    override fun close() {
        approvedTool.set(null)
        appIdleClosureArmed.set(false)
        approvedAppIdleClose.set(false)
    }
}
