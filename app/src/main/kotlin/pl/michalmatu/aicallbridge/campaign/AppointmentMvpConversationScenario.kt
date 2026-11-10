package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import pl.michalmatu.aicallbridge.agent.*
import pl.michalmatu.aicallbridge.appointment.*
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.identity.*
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.textagent.*

/**
 * Live outbound speech adapter for one user-approved appointment task.
 * Only proposes review; has NO route to a provider booking/commitment.
 */
internal class AppointmentMvpConversationScenario(
    private val context: Context,
    private val task: AppointmentMvpTask,
    private val configuration: ConversationRunConfiguration.ModelDirect,
) : ConversationScenario {
    override val id = "appointment-mvp"
    override val preparationId = configuration.preparation.id
    override val direction = ConversationDirection.OUTGOING
    override val mode = ConversationRuntimeMode.OWNED_TASK
    override val responseKind = ConversationChatFeed.ResponseKind.MODEL_DIRECT
    // First S22 call: second RX ran 55.28s and was lost when remote ended.
    // Bound clinic/IVR segments, not the phone call; keep existing silence VAD.
    override val maxRxCaptureMs: Int = 20_000
    override fun readinessFailure(): String? = null

    override fun open(hooks: ConversationRuntimeHooks): ConversationDialogueSession {
        val target = CallResolvedTarget(task.goal, task.target)
        val callTask = task.toCallTask()
        val workflow = CallWorkflow(callTask, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)
        workflow.markDialing()
        workflow.markCallActive()
        val commitmentGate = CallCommitmentGate()
        val reviewReducer = AppointmentMvpReviewReducer(task)
        val reviewStore = AppointmentMvpStore(context)
        val vault = AndroidIdentityVault.create(context)
        val interpreter = AppointmentInterpreter(ZoneId.of("Europe/Warsaw"))
        val model = configuration.acquireModel(
            takePrepared = { profile, instruction ->
                CampaignGemmaDirectPreflight.takePrepared(instruction, profile)
            },
            create = { profile, instruction ->
                DialogueEngineProviders.resolve(profile).createDirect(context, instruction)
            },
        )
        val approved = CallTextAgentOutputApprovalPolicy(workflow, commitmentGate)
        return object : ConversationDialogueSession {
            private var reviewPending = false
            private var reviewed = false
            private var currentReview: AppointmentMvpReview? = null
            override val openingText: String =
                "Dzień dobry, dzwonię w imieniu użytkownika. " + task.goal
            override val approvalPolicy = TextOutputApprovalPolicy { text ->
                if (!hooks.authorized() || text.isBlank() ||
                    text.contains("[[") || text.contains("]]")) TextOutputDecision.DROP
                else approved.evaluate(text)
            }
            override val backend: TextCallAgentBackend = object : TextCallAgentBackend {
                override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                    if (!hooks.authorized()) {
                        listener.onError("appointment_authority_revoked")
                        return
                    }
                    if (reviewPending) {
                        listener.onComplete("Dziękuję. Zanotowałam proponowany termin do " +
                            "weryfikacji przez użytkownika. Nie potwierdzam rezerwacji.")
                        return
                    }
                    val field = interpreter.parseIdentityFieldRequest(userText)?.fieldId
                    if (field != null) {
                        discloseRequestedField(field, listener)
                        return
                    }
                    model.generate(userText, listener)
                }
                override fun cancel() = model.cancel()
                override fun close() = model.close()
            }
            private fun discloseRequestedField(
                field: IdentityFieldId, listener: TextCallAgentBackend.Listener,
            ) {
                if (field !in task.disclosureFields ||
                    field !in configuration.preparation.dataFields) {
                    listener.onComplete("Nie mam zgody na przekazanie tej informacji. " +
                        "Proszę o możliwość kontynuowania bez tych danych.")
                    return
                }
                val generation = hooks.mediaGeneration()
                if (generation <= 0L || !hooks.authorized()) {
                    listener.onError("appointment_identity_generation_unbound")
                    return
                }
                val snapshot = AuthorizedFactSnapshot(
                    task = callTask, target = target, generation = generation,
                    availableFields = vault.availableFields().getOrNull().orEmpty(),
                    authorizedFields = task.disclosureFields,
                    allowedDisclosureStates = task.disclosureFields.associateWith {
                        setOf(BookAppointmentTaskGraph.WAITING_OFFER)
                    },
                )
                val request = FactDisclosureRequest(
                    task = callTask, target = target,
                    currentState = BookAppointmentTaskGraph.WAITING_OFFER,
                    fieldId = field, mode = CallTaskMode.GENUINE,
                    snapshotGeneration = generation,
                )
                val read = ScopedIdentityFactReader(
                    vault = vault, policy = DefaultFactDisclosurePolicy(),
                    activeAuthority = { req ->
                        req == request && task.disclosureFields.contains(req.fieldId) &&
                            generation == hooks.mediaGeneration() && hooks.authorized()
                    },
                ).read(request, snapshot)
                val speech = when (read) {
                    is ScopedIdentityFactRead.Authorized ->
                        read.secret.withPlaintext { it }
                    ScopedIdentityFactRead.ApprovalRequired ->
                        "To wymaga osobnej zgody użytkownika."
                    ScopedIdentityFactRead.Denied ->
                        "Nie mogę udostępnić tej informacji."
                }
                if (!hooks.authorized() || generation != hooks.mediaGeneration()) {
                    listener.onError("appointment_identity_authority_revoked")
                } else listener.onComplete(speech)
            }
            override fun start() = Unit
            override fun beginTurn() = Unit
            override fun onTranscript(turn: Int, text: String) {
                if (reviewPending || !hooks.authorized()) return
                val proposed = reviewReducer.observe(text,
                    LocalDate.now(ZoneId.of("Europe/Warsaw"))) ?: return
                if (reviewStore.saveReview(task.grantId, proposed) && hooks.authorized()) {
                    reviewPending = true
                    currentReview = proposed
                    hooks.emit("appointment_review_required=true")
                }
            }
            override fun takeOutcome(boundary: ConversationOutcomeBoundary): ConversationOutcome? {
                if (boundary == ConversationOutcomeBoundary.PLAYBACK_COMPLETE &&
                    reviewPending && hooks.authorized()) {
                    reviewed = true
                    return ConversationOutcome.Completed
                }
                return null
            }
            override fun turnBudgetOutcome() =
                ConversationOutcome.Failed("appointment_review_not_observed")
            override fun recordTurnDiagnostics(turn: Int) = Unit
            override fun finalDiagnostics() = listOf(
                "appointment_review_required=" + reviewed,
                "appointment_external_booking_confirmed=false",
                "appointment_provider_effects=false",
            )
            override fun close() {
                commitmentGate.clear()
            }
        }
    }
}
