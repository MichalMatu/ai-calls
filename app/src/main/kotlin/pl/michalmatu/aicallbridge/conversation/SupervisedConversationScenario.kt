package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger
import pl.michalmatu.aicallbridge.agent.*
import pl.michalmatu.aicallbridge.developerrelay.SupervisedGemmaDialogueBackend
import pl.michalmatu.aicallbridge.developerrelay.SupervisedReadOnlyDialogueBackend
import pl.michalmatu.aicallbridge.textagent.*

internal class SupervisedConversationScenario(private val context: Context, private val modelEnabled: Boolean) : ConversationScenario {
    override val id = "supervised-audio"
    override val preparationId = "supervised-audio"
    override val direction = ConversationDirection.UNSPECIFIED
    override val mode = ConversationRuntimeMode.SUPERVISED_ATTACH
    override val responseKind = if (modelEnabled) ConversationChatFeed.ResponseKind.MODEL_DIRECT else ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED
    override fun readinessFailure(): String? = if (modelEnabled &&
        AndroidGemma4ModelReadiness.check(context).state != Gemma4ModelReadinessState.READY) "gemma_model_not_ready" else null
    override fun open(hooks: ConversationRuntimeHooks): ConversationDialogueSession = object : ConversationDialogueSession {
        private val replies = AtomicInteger(0)
        private val fallbacks = AtomicInteger(0)
        private val task = CallTask("Supervised sound test", "READ_ONLY_AUDIO_TEST", "audio",
            CallConstraints.unconstrained(), CallPreferences.none(), emptyMap())
        private val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(CallResolvedTarget("User-controlled existing call", "manual-call"))
            markDialing(); markCallActive()
        }
        override val backend: TextCallAgentBackend = if (modelEnabled) SupervisedGemmaDialogueBackend(
            context, { replies.incrementAndGet() }, { fallbacks.incrementAndGet() }) else SupervisedReadOnlyDialogueBackend()
        override val approvalPolicy = CallTextAgentOutputApprovalPolicy(workflow, CallCommitmentGate { "supervised-no-effects" })
        override fun start() = Unit
        override fun beginTurn() = Unit
        override fun onTranscript(turn: Int, text: String) = Unit
        override fun takeOutcome(boundary: pl.michalmatu.aicallbridge.conversation.ConversationOutcomeBoundary): ConversationOutcome? = null
        override fun turnBudgetOutcome() = ConversationOutcome.Completed
        override fun recordTurnDiagnostics(turn: Int) = Unit
        override fun finalDiagnostics() = listOf("supervised_gemma_replies=${replies.get()}", "supervised_script_fallback_replies=${fallbacks.get()}")
        override fun close() = Unit
    }
}
