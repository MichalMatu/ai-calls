package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.textagent.*

/** Model-directed live enquiry on the authorized owned call; never commits external effects. */
internal class ScenarioOutgoingConversationScenario(
    private val context: Context,
    private val task: ScenarioOutgoingTask,
    private val configuration: ConversationRunConfiguration.ModelDirect,
) : ConversationScenario {
    override val id: String = "scenario-enquiry-" + task.scenarioId
    override val preparationId: String = configuration.preparation.id
    override val direction = ConversationDirection.OUTGOING
    override val mode = ConversationRuntimeMode.OWNED_TASK
    override val responseKind = ConversationChatFeed.ResponseKind.MODEL_DIRECT
    override val maxRxCaptureMs: Int = 20_000
    override fun readinessFailure(): String? = null

    override fun open(hooks: ConversationRuntimeHooks): ConversationDialogueSession {
        val model = configuration.acquireModel(
            takePrepared = { profile, instruction ->
                CampaignGemmaDirectPreflight.takePrepared(instruction, profile)
            },
            create = { profile, instruction ->
                DialogueEngineProviders.resolve(profile).createDirect(context, instruction)
            },
        )
        return object : ConversationDialogueSession {
            override val openingText =
                "Dzień dobry, dzwonię w imieniu użytkownika w sprawie: " + task.title + "."
            override val approvalPolicy = TextOutputApprovalPolicy { speech ->
                val forbiddenCommitment = Regex(
                    "(?iu)\\b(potwierdzam rezerwację|rezerwuję|zamawiam|kupuję|" +
                        "akceptuję opłatę|zgadzam się na opłatę|i hereby book|" +
                        "i confirm the booking|i accept the charge)\\b",
                ).containsMatchIn(speech)
                if (!hooks.authorized() || speech.isBlank() ||
                    speech.contains("[[") || speech.contains("]]") || forbiddenCommitment
                ) TextOutputDecision.DROP else TextOutputDecision.RELEASE
            }
            override val backend: TextCallAgentBackend = object : TextCallAgentBackend {
                override fun generate(input: String, listener: TextCallAgentBackend.Listener) {
                    if (!hooks.authorized()) {
                        listener.onError("scenario_authority_revoked")
                        return
                    }
                    model.generate(input, listener)
                }
                override fun cancel() = model.cancel()
                override fun close() = model.close()
            }
            override fun start() = Unit
            override fun beginTurn() = Unit
            override fun onTranscript(turn: Int, text: String) = Unit
            override fun takeOutcome(boundary: ConversationOutcomeBoundary): ConversationOutcome? = null
            override fun turnBudgetOutcome() = ConversationOutcome.Completed
            override fun recordTurnDiagnostics(turn: Int) = Unit
            override fun finalDiagnostics() = listOf(
                "scenario_template_id=" + task.scenarioId,
                "scenario_user_review_required=true",
                "scenario_external_effect_confirmed=false",
            )
            override fun close() = Unit
        }
    }
}
