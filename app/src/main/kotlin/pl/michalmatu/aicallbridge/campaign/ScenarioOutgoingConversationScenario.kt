package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.textagent.*
import pl.michalmatu.aicallbridge.tools.*
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

/** Model-directed live enquiry on the authorized owned call; never commits external effects. */
internal class ScenarioOutgoingConversationScenario(
    private val context: Context,
    private val task: ScenarioOutgoingTask,
    private val configuration: ConversationRunConfiguration.ModelDirect,
    private val requestEndCall: () -> Boolean = { false },
) : ConversationScenario {
    override val id: String = "scenario-enquiry-" + task.scenarioId
    override val preparationId: String = configuration.preparation.id
    override val direction = ConversationDirection.OUTGOING
    override val mode = ConversationRuntimeMode.OWNED_TASK
    override val responseKind = ConversationChatFeed.ResponseKind.MODEL_DIRECT
    override val maxRxCaptureMs: Int = 20_000
    override fun readinessFailure(): String? = null

    override fun open(hooks: ConversationRuntimeHooks): ConversationDialogueSession {
        val enabled = ConversationToolSettingsStore(context).enabled()
        val model = configuration.acquireModel(
            takePrepared = { profile, instruction ->
                CampaignGemmaDirectPreflight.takePrepared(instruction, profile, enabled)
            },
            create = { profile, instruction ->
                DialogueEngineProviders.resolve(profile)
                    .createConversationReply(context, instruction, enabled)
            },
        )
        val approval = TextOutputApprovalPolicy { speech ->
            val forbiddenCommitment = Regex(
                "(?iu)\\b(potwierdzam rezerwację|rezerwuję|zamawiam|kupuję|" +
                    "akceptuję opłatę|zgadzam się na opłatę|i hereby book|" +
                    "i confirm the booking|i accept the charge)\\b",
            ).containsMatchIn(speech)
            if (!hooks.authorized() || speech.isBlank() ||
                speech.contains("[[") || speech.contains("]]") || forbiddenCommitment
            ) TextOutputDecision.DROP else TextOutputDecision.RELEASE
        }
        val session = ToolCallingConversationSession(
            model = object : TextCallAgentBackend {
                override fun generate(input: String, listener: TextCallAgentBackend.Listener) {
                    if (!hooks.authorized()) listener.onError("scenario_authority_revoked")
                    else model.generate(input, listener)
                }
                override fun cancel() = model.cancel()
                override fun close() = model.close()
            },
            releasePolicy = approval,
            tools = createOutgoingConversationTools(context, hooks, requestEndCall),
            hooks = hooks,
            responseSource = "OUTGOING_SCENARIO",
            openingText = "Dzień dobry, dzwonię w imieniu użytkownika w sprawie: " +
                task.title + ".",
        )
        return object : ConversationDialogueSession by session {
            override fun finalDiagnostics() = session.finalDiagnostics() + listOf(
                "scenario_template_id=" + task.scenarioId,
                "scenario_user_review_required=true",
                "scenario_external_effect_confirmed=false",
            )
        }
    }
}
