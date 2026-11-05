package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.campaign.ClirCarrierEvidence
import pl.michalmatu.aicallbridge.campaign.ClirDeterministicDialogueBackend
import pl.michalmatu.aicallbridge.campaign.CampaignGemmaDirectPreflight
import pl.michalmatu.aicallbridge.campaign.CampaignGemmaPreflight
import pl.michalmatu.aicallbridge.campaign.ClirRouteEvidence

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import pl.michalmatu.aicallbridge.GateCHybridDiagnostics
import pl.michalmatu.aicallbridge.GateCHybridDialogueBackendFactory
import pl.michalmatu.aicallbridge.agent.CallCommitmentConsumptionEvidence
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallExternalEffect
import pl.michalmatu.aicallbridge.agent.CallExternalEffectCompletionTracker
import pl.michalmatu.aicallbridge.agent.CallExternalEffectValidation
import pl.michalmatu.aicallbridge.agent.CallExternalEffectValidator
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallService
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.DefaultFactDisclosurePolicy
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.textagent.AuthorizedPhoneFactBackend
import pl.michalmatu.aicallbridge.textagent.AuthorizedPhoneFactScope
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.developerrelay.*


/** Outgoing CLIR behaviour and effect authority; independent of media transport. */
internal class ClirConversationScenario(
    private val context: Context,
    private val sessionId: String,
    private val runConfiguration: ConversationRunConfiguration,
    private val phoneDisclosureAuthorized: Boolean,
    private val supervisorRelayEnabled: Boolean = false,
) : ConversationScenario {
    override val id = "clir-enable"
    override val preparationId = runConfiguration.preparation.id
    override val direction = ConversationDirection.OUTGOING
    override val mode = if (supervisorRelayEnabled) ConversationRuntimeMode.DEVELOPER_RELAY else ConversationRuntimeMode.OWNED_TASK
    override val responseKind = if (runConfiguration is ConversationRunConfiguration.ModelDirect)
        ConversationChatFeed.ResponseKind.MODEL_DIRECT else ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED
    override fun readinessFailure(): String? = null // The campaign has already performed selected-model prewarm.
    override fun open(hooks: ConversationRuntimeHooks): ConversationDialogueSession = Dialogue(hooks)

    private inner class Dialogue(private val hooks: ConversationRuntimeHooks) : ConversationDialogueSession {
        private val mailbox = ChatRelayMailbox(File(context.filesDir, ChatRelayMailbox.DIRECTORY_NAME))
        private val target = CallResolvedTarget("Orange support", ClirCampaignSpec.TARGET)
        private val task = CallTask("Orange CLIR enable", "SET_SERVICE", "CLIR",
            CallConstraints.unconstrained(), CallPreferences.none(),
            mapOf(CallExternalEffectValidator.SERVICE_ENABLED_FACT to "true"))
        private val effect by lazy { CallExternalEffect.SetService(target, CallService.CLIR, true) }
        private var turnDecisionBaseline = 0
        private var turnResponseSourceBaseline = 0
        private var turnClassifierTimingBaseline = 0
        private var turnLocalSkillErrorBaseline = 0
        private val lastPrivateDecisionTraceTurn = AtomicInteger(0)
        private val workflow = activeWorkflow(task, target)
        private val commitmentGate = CallCommitmentGate { "clir-live-${UUID.randomUUID()}" }
        private val hybridDiagnostics = GateCHybridDiagnostics()
        private val routeVerified = AtomicBoolean(false)
        private val commitmentConsumed = AtomicBoolean(false)
        private val identityVault by lazy { AndroidIdentityVault.create(context) }
        private val factDisclosurePolicy = DefaultFactDisclosurePolicy()
        private val phoneDisclosureState = TaskGraphStateId("clir-service-number-request")
        private val localPhoneFactBackend by lazy { AuthorizedPhoneFactBackend(
            vault = identityVault,
            policy = factDisclosurePolicy,
            scopeProvider = {
                AuthorizedPhoneFactScope(
                    task = task,
                    target = target,
                    state = phoneDisclosureState,
                    generation = hooks.mediaGeneration(),
                    mode = CallTaskMode.GENUINE,
                    authorized = phoneDisclosureAuthorized && routeVerified.get() &&
                        hooks.mediaGeneration() > 0L && hooks.authorized(),
                )
            },
            onDisclosure = { hooks.emit("identity_phone_disclosure_local=true") },
        ) }
        @Volatile private var completionTracker: CallExternalEffectCompletionTracker? = null
        private val pendingExternalSuccessEvidence = AtomicBoolean(false)
        private val hybridBackend by lazy { GateCHybridDialogueBackendFactory.create(
            context = context,
            provider = TextLlmProvider.LOCAL_GEMMA_4,
            relaySessionId = sessionId,
            diagnostics = hybridDiagnostics,
            authorizedFactBackend = localPhoneFactBackend,
            effectCommitControl = COMMIT_CLIR_ENABLE,
            canConfirmEffect = {
                hooks.mediaGeneration() > 0L && hooks.authorized() &&
                    routeVerified.get() && !commitmentConsumed.get()
            },
            supervisorRelayEnabled = supervisorRelayEnabled,
            // Only the app-owned campaign may consume its already initialized engine.
            // Supervised and developer probes keep independent model ownership.
            preparedClassifier = (if (!supervisorRelayEnabled) {
                CampaignGemmaPreflight.takePrepared(runConfiguration.audioProfile)
            } else null) ?: CampaignGemmaPreflight.createClassifier(context, runConfiguration.audioProfile),
        ) }
        private val directBackend by lazy {
            val direct = runConfiguration as ConversationRunConfiguration.ModelDirect
            GemmaDirectDialogueBackendFactory.create(
                model = direct.acquireModel(
                    takePrepared = { profile, instruction ->
                        CampaignGemmaDirectPreflight.takePrepared(instruction, profile)
                    },
                    create = { profile, instruction ->
                        DialogueEngineProviders.resolve(profile).createDirect(context, instruction)
                    },
                ),
                authorizedPhoneFact = localPhoneFactBackend,
                canConfirmEffect = {
                    hooks.mediaGeneration() > 0L &&
                        hooks.authorized() && routeVerified.get() && !commitmentConsumed.get()
                },
                observer = GemmaDirectObserver { action, source, elapsedMs ->
                    hooks.emit("turn_${hooks.turn()}_direct_action=$action")
                    hooks.emit("turn_${hooks.turn()}_direct_response_source=$source")
                    hooks.emit("turn_${hooks.turn()}_direct_inference_ms=$elapsedMs")
                    hooks.decisionSink.record(
                        turn = hooks.turn(),
                        actionId = action,
                        responseSource = source,
                        classifierWallMs = elapsedMs,
                        localErrorPresent = source == "DIRECT_REJECTED" ||
                            source == "DIRECT_MODEL_ERROR",
                    )
                },
            )
        }
        override val backend: TextCallAgentBackend = when (runConfiguration) {
            is ConversationRunConfiguration.ModelDirect -> directBackend
            is ConversationRunConfiguration.ScriptAssisted -> controlAwareBackend(
                ClirDeterministicDialogueBackend(hybridBackend) { ruleId ->
                    hooks.emit("clir_phrase_matrix_rule=$ruleId")
                },
            )
        }
        override val approvalPolicy = CallTextAgentOutputApprovalPolicy(workflow, commitmentGate)

        override fun start() { mailbox.clear() }
        override fun beginTurn() {
            val snapshot = hybridDiagnostics.snapshot()
            turnDecisionBaseline = snapshot.decisions.size
            turnResponseSourceBaseline = snapshot.responseSources.size
            turnLocalSkillErrorBaseline = snapshot.localSkillErrors.size
            turnClassifierTimingBaseline = snapshot.classifierLatenciesMs.size
        }
        override fun onTranscript(turn: Int, text: String) {
            if (ClirRouteEvidence.isExplicitServiceContext(text)) {
                hooks.emit("turn_${turn}_clir_route_evidence=true")
                if (routeVerified.compareAndSet(false, true)) hooks.emit("clir_route_verified=true")
            }
            if (commitmentConsumed.get() && ClirCarrierEvidence.isConfirmedCarrierStatement(text)) {
                pendingExternalSuccessEvidence.set(true)
                hooks.emit("clir_success_evidence_detected=true")
            }
        }
        override fun takeOutcome(): ConversationOutcome? {
            if (!pendingExternalSuccessEvidence.compareAndSet(true, false)) return null
            if (!hooks.authorized()) return ConversationOutcome.Failed("campaign_authority_revoked_or_expired")
            if (completionTracker == null) return ConversationOutcome.Failed("clir_completion_tracker_missing")
            hooks.emit("clir_carrier_reported_success=true")
            hooks.emit("clir_network_verification=pending")
            return ConversationOutcome.Completed
        }
        override fun turnBudgetOutcome() = ConversationOutcome.Failed("clir_external_success_not_observed")
        override fun finalDiagnostics(): List<String> {
            val snapshot = hybridDiagnostics.snapshot()
            return listOf(
                "gemma_action_decision_count=${snapshot.decisions.size}",
                "gemma_skill_decision_count=${snapshot.decisions.size}",
                "host_action_count=${snapshot.responseSources.count { it.name == "HOST_ACTION" }}",
                "chatgpt_fallback_count=${snapshot.responseSources.count { it.name == "CHAT_RELAY" }}",
                "clir_route_verified=${routeVerified.get()}",
                "clir_commitment_consumed=${commitmentConsumed.get()}",
                "clir_external_success=false",
                "clir_commit_control=$COMMIT_CLIR_ENABLE",
                "phone_disclosure_authorized=$phoneDisclosureAuthorized",
                "supervised_gemma_replies=0", "supervised_script_fallback_replies=0",
            )
        }
        override fun close() { mailbox.clear() }
        private fun controlAwareBackend(delegate: TextCallAgentBackend): TextCallAgentBackend =
            object : TextCallAgentBackend {
                override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                    delegate.generate(userText, object : TextCallAgentBackend.Listener {
                        override fun onComplete(text: String) {
                            try {
                                when (text.trim()) {
                                    COMMIT_CLIR_ENABLE -> listener.onComplete(commitClirEnable())
                                    else -> listener.onComplete(text)
                                }
                            } catch (error: Throwable) {
                                listener.onError("clir_control_${error.javaClass.simpleName}")
                            }
                        }

                        override fun onError(reason: String) = listener.onError(reason)
                    })
                }

                override fun cancel() = delegate.cancel()
                override fun close() = delegate.close()
            }

        private fun commitClirEnable(): String {
            require(hooks.authorized()) { "campaign_authority_revoked_or_expired" }
            require(hooks.mediaGeneration() > 0L) { "media_generation_unbound" }
            require(routeVerified.get()) { "clir_route_not_verified" }
            require(!commitmentConsumed.get()) { "clir_commitment_already_consumed" }
            val validation = CallExternalEffectValidator.validateSetService(task, target, effect)
            require(validation is CallExternalEffectValidation.Accepted) { "clir_effect_validation_rejected" }
            val authorization = commitmentGate.authorize(validation.effect)
            hooks.emit("clir_effect_permit_issued=true")
            val consumed = commitmentGate.consumeEffect(authorization.value).getOrThrow()
            require(consumed == effect && !commitmentGate.hasAuthorization()) {
                "clir_effect_consumption_mismatch"
            }
            completionTracker = CallExternalEffectCompletionTracker.fromConsumption(
                CallCommitmentConsumptionEvidence(consumed),
            )
            commitmentConsumed.set(true)
            hooks.emit("clir_effect_permit_consumed=true")
            return REVIEWED_CLIR_COMMIT_SPEECH
        }

        override fun recordTurnDiagnostics(turn: Int) {
            val snapshot = hybridDiagnostics.snapshot()
            val decision = snapshot.decisions.drop(turnDecisionBaseline).lastOrNull()
            val source = snapshot.responseSources.drop(turnResponseSourceBaseline).lastOrNull()
            val classifierMs = snapshot.classifierLatenciesMs.drop(turnClassifierTimingBaseline).lastOrNull()
            val localErrorPresent = snapshot.localSkillErrors.drop(turnLocalSkillErrorBaseline).isNotEmpty()
            decision?.let {
                hooks.emit("turn_${turn}_dialogue_action=${it.actionId.name}")
                hooks.emit("turn_${turn}_dialogue_confidence=${it.confidence}")
                if (it.argument != null) {
                    hooks.emit("turn_${turn}_dialogue_argument_present=true")
                }
            }
            source?.let { hooks.emit("turn_${turn}_response_source=${it.name}") }
            classifierMs?.let { hooks.emit("turn_${turn}_gemma_classifier_wall_ms=$it") }
            if (localErrorPresent) hooks.emit("turn_${turn}_local_error_present=true")
            if ((decision != null || source != null || classifierMs != null || localErrorPresent) &&
                lastPrivateDecisionTraceTurn.getAndSet(turn) != turn
            ) {
                hooks.decisionSink.record(
                    turn = turn,
                    actionId = decision?.actionId?.name,
                    responseSource = source?.name,
                    classifierWallMs = classifierMs,
                    localErrorPresent = localErrorPresent,
                )
            }
        }

    }

    private fun activeWorkflow(task: CallTask, target: CallResolvedTarget): CallWorkflow {
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(target)
        workflow.markDialing()
        workflow.markCallActive()
        return workflow
    }

    private companion object {
        const val COMMIT_CLIR_ENABLE = "[[COMMIT_CLIR_ENABLE]]"
        const val REVIEWED_CLIR_COMMIT_SPEECH = "Potwierdzam. Proszę włączyć blokadę prezentacji numeru, usługę CLIR."
    }
}
