package pl.michalmatu.aicallbridge.localcall

import android.content.Context
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueHypothesis
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObservation
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObserver
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalValidation
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy

/**
 * Narrow product-owned local dialogue session. Telephony media ownership and endpointing stay
 * outside this class; the session owns the already-proven backend/speech pipeline plus an optional
 * deterministic CallPlan turn coordinator carried by the prepared call.
 *
 * CallPlan routing remains structured only here: this class does not synthesize or transmit plan
 * output directly, approve proposals, execute external commitments, or fall back to a backend/model.
 * Gate D activation remains explicit. The public Android create path binds neither a shadow
 * observer nor product apply wiring; reviewed internal composition may opt into exactly one of
 * those paths. The bounded BOOK_APPOINTMENT methods below only delegate to existing application
 * owners and do not dial, speak, disclose identity, execute a commitment, or complete a workflow.
 */
internal class LocalTextCallSession private constructor(
    private val workflow: CallWorkflow,
    private val pipeline: Pipeline,
    private val planTurnCoordinator: CallPlanTurnCoordinator?,
    phraseMatrix: PhraseMatrix?,
    private val gateDRuntime: LocalTextCallGateDRuntime?,
    gateDDefinition: TaskGraphDefinition?,
    gateDSnapshot: TaskGraphSnapshot?,
    gateDMaxRecoveryCount: Int,
    gateDShadowDependencies: GateDShadowDependencies?,
    gateDProductBinding: LocalTextCallGateDProductBinding?,
) : AutoCloseable {
    internal interface Pipeline : AutoCloseable {
        fun start(listener: LocalSpeechTextPipeline.Listener)

        fun start(
            listener: LocalSpeechTextPipeline.Listener,
            finalTurnRouteSelector: LocalSpeechFinalTurnRouteSelector,
        ) {
            error("final_turn_route_selector_not_supported")
        }

        fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean
        fun finishInput()
        fun cancel()
    }

    internal fun interface PipelineFactory {
        fun create(backend: TextCallAgentBackend): Pipeline
    }

    internal fun interface PlanTurnListener {
        fun onStructuredResult(result: CallPlanTurnResult)
    }

    /**
     * Narrow cross-package diagnostic view. It exposes only the bounded action and validated rule id;
     * CallPlanTurnResult and its payload-bearing internals remain package-private to localcall.
     */
    internal fun interface StructuredPlanTurnListener {
        fun onStructuredDecision(action: CallPlanAction, ruleId: String?)
    }

    private data class GateDShadowDependencies(
        val observer: ShadowDialogueObserver,
        val executor: GateDShadowExecutor,
        val diagnosticsListener: GateDShadowDiagnosticsListener,
    )

    private val phraseMatrixTurnRouter: PhraseMatrixProductTurnRouter? =
        if (phraseMatrix != null) {
            PhraseMatrixProductTurnRouter(
                phraseMatrix,
                checkNotNull(planTurnCoordinator) { "phrase_matrix_requires_call_plan" },
            )
        } else {
            null
        }

    private val gateDShadowLifecycle: LocalTextCallGateDShadowLifecycle? =
        gateDShadowDependencies?.let { dependencies ->
            LocalTextCallGateDShadowLifecycle(
                runtime = checkNotNull(gateDRuntime) { "gate_d_shadow_requires_runtime" },
                authoritativeSnapshot = checkNotNull(gateDSnapshot) {
                    "gate_d_shadow_requires_snapshot"
                },
                maxRecoveryCount = gateDMaxRecoveryCount,
                observer = dependencies.observer,
                executor = dependencies.executor,
                diagnosticsListener = dependencies.diagnosticsListener,
            )
        }

    private val gateDProductIntegration: LocalTextCallGateDProductIntegration? =
        gateDProductBinding?.let { binding ->
            require(gateDShadowDependencies == null) {
                "gate_d_shadow_and_product_integration_are_mutually_exclusive"
            }
            LocalTextCallGateDProductIntegration(
                runtime = checkNotNull(gateDRuntime) { "gate_d_product_requires_runtime" },
                definition = checkNotNull(gateDDefinition) { "gate_d_product_requires_definition" },
                initialSnapshot = checkNotNull(gateDSnapshot) { "gate_d_product_requires_snapshot" },
                maxRecoveryCount = gateDMaxRecoveryCount,
                binding = binding,
            )
        }

    private val planFallbackLock = Any()
    private var consecutiveUnknownCount = 0
    private var previousValidatedRuleId: String? = null

    private constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
        gateDShadowDependencies: GateDShadowDependencies?,
        gateDProductBinding: LocalTextCallGateDProductBinding?,
    ) : this(
        workflow = prepared.workflow,
        pipeline = claimPipeline(prepared, pipelineFactory),
        planTurnCoordinator = prepared.callPlan?.let { CallPlanTurnCoordinator(it, prepared.workflow) },
        phraseMatrix = prepared.phraseMatrix,
        gateDRuntime = prepared.taskGraph?.let { graph ->
            LocalTextCallGateDRuntime(
                task = prepared.workflow.snapshot().task,
                graph = graph,
                authorizedFacts = prepared.authorizedFacts,
            )
        },
        gateDDefinition = prepared.taskGraph,
        gateDSnapshot = prepared.taskGraph?.initialSnapshot(),
        gateDMaxRecoveryCount = prepared.taskGraph?.maxRecoveryCount ?: 0,
        gateDShadowDependencies = gateDShadowDependencies,
        gateDProductBinding = gateDProductBinding,
    )

    internal constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
    ) : this(
        prepared = prepared,
        pipelineFactory = pipelineFactory,
        gateDShadowDependencies = null,
        gateDProductBinding = null,
    )

    internal constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
        gateDShadowObserver: ShadowDialogueObserver,
        gateDShadowExecutor: GateDShadowExecutor,
        gateDShadowDiagnosticsListener: GateDShadowDiagnosticsListener,
    ) : this(
        prepared = prepared,
        pipelineFactory = pipelineFactory,
        gateDShadowDependencies = GateDShadowDependencies(
            observer = gateDShadowObserver,
            executor = gateDShadowExecutor,
            diagnosticsListener = gateDShadowDiagnosticsListener,
        ),
        gateDProductBinding = null,
    )

    internal constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
        gateDProductBinding: LocalTextCallGateDProductBinding,
    ) : this(
        prepared = prepared,
        pipelineFactory = pipelineFactory,
        gateDShadowDependencies = null,
        gateDProductBinding = gateDProductBinding,
    )

    fun start(listener: LocalSpeechTextPipeline.Listener) {
        check(planTurnCoordinator == null) { "call_plan_turn_listener_required" }
        pipeline.start(listener)
    }

    fun start(
        listener: LocalSpeechTextPipeline.Listener,
        planTurnListener: PlanTurnListener,
    ) {
        if (planTurnCoordinator == null) {
            pipeline.start(listener)
            return
        }
        pipeline.start(
            listener,
            LocalSpeechFinalTurnRouteSelector { finalTranscript ->
                val selection = processFinalizedTranscript(finalTranscript)
                selection.structuredResult?.let(planTurnListener::onStructuredResult)
                selection.route
            },
        )
    }

    fun startWithPlanRouting(
        listener: LocalSpeechTextPipeline.Listener,
        structuredListener: StructuredPlanTurnListener,
    ) {
        start(
            listener,
            PlanTurnListener { result ->
                val decision = result.decision()
                structuredListener.onStructuredDecision(decision.action(), decision.ruleId())
            },
        )
    }

    fun writeInputPcm(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ): Boolean = pipeline.writeInputPcm(bytes, offset, length)

    fun finishInput() = pipeline.finishInput()

    /**
     * Explicit test/diagnostic ingress for one already-finalized transcript. It uses the exact same
     * PhraseMatrix/CallPlan + Gate D finalized-turn processing as an STT-finalized turn, while
     * bypassing pipeline start, STT/audio input, backend generation and TTS/media output.
     *
     * Changing the source of finalized text adds no authority beyond the normal STT-finalized path.
     * Any structured CallPlan/workflow behavior remains subject to the same existing owners and
     * policies; this ingress creates no separate dialing, target-widening, disclosure, output,
     * confirmation, commitment or completion path.
     */
    internal fun injectSyntheticFinalTranscript(finalTranscript: String): CallPlanFinalTurnSelection =
        processFinalizedTranscript(finalTranscript)

    /**
     * Explicit application-owned BOOK_APPOINTMENT confirmation/rejection entry point.
     *
     * This is intentionally separate from counterparty finalized text and creates no commitment
     * permit. The product integration re-checks the exact graph state and pending workflow proposal
     * before the existing CallWorkflow owner consumes the user's decision.
     */
    internal fun applyBookAppointmentUserDecision(
        decision: GateDBookAppointmentUserDecision,
    ): GateDBookAppointmentUserDecisionResult {
        val integration = gateDProductIntegration
            ?: return GateDBookAppointmentUserDecisionResult.Rejected(
                GateDBookAppointmentUserDecisionRejectReason.PRODUCT_INTEGRATION_NOT_BOUND,
            )
        return try {
            integration.applyBookAppointmentUserDecision(workflow, decision)
        } catch (_: Throwable) {
            GateDBookAppointmentUserDecisionResult.Rejected(
                GateDBookAppointmentUserDecisionRejectReason.INTERNAL_FAILURE,
            )
        }
    }

    /**
     * Requests one opaque BOOK_APPOINTMENT commitment permit after explicit user confirmation.
     * The permit is not consumed here and this method does not advance or complete the workflow.
     */
    internal fun authorizeBookAppointmentCommitment():
        GateDBookAppointmentCommitmentAuthorizationResult {
        val integration = gateDProductIntegration
            ?: return GateDBookAppointmentCommitmentAuthorizationResult.Rejected(
                GateDBookAppointmentCommitmentAuthorizationRejectReason.PRODUCT_INTEGRATION_NOT_BOUND,
            )
        return try {
            integration.authorizeBookAppointmentCommitment()
        } catch (_: Throwable) {
            GateDBookAppointmentCommitmentAuthorizationResult.Rejected(
                GateDBookAppointmentCommitmentAuthorizationRejectReason.INTERNAL_FAILURE,
            )
        }
    }

    /**
     * Routes one already-final transcript through the optional PhraseMatrix fast path and then the
     * bound CallPlan while owning all bounded dialogue context for this session. ASK_REPEAT advances
     * the consecutive-unknown counter; every other deterministic result resets it. Previous-rule
     * context is retained only from a validated SAY decision and is cleared by fallback/structured
     * actions, so raw matcher ids never become session authority.
     */
    fun handlePlanFinalTranscript(finalTranscript: String): CallPlanTurnResult =
        synchronized(planFallbackLock) {
            val coordinator = checkNotNull(planTurnCoordinator) { "call_plan_not_bound" }
            val result = phraseMatrixTurnRouter
                ?.handleFinalTranscript(
                    finalTranscript = finalTranscript,
                    priorUnknownCount = consecutiveUnknownCount,
                    previousRuleId = previousValidatedRuleId,
                )
                ?.turnResult
                ?: coordinator.handleFinalTranscript(finalTranscript, consecutiveUnknownCount)

            consecutiveUnknownCount = if (result.decision().action() == CallPlanAction.ASK_REPEAT) {
                consecutiveUnknownCount + 1
            } else {
                0
            }
            previousValidatedRuleId = if (result.decision().action() == CallPlanAction.SAY) {
                result.decision().ruleId()
            } else {
                null
            }
            result
        }

    /**
     * Explicit stateless CallPlan entry point retained for focused diagnostics/tests. Product
     * session routing should use the overload that owns the consecutive-unknown counter and
     * previous validated PhraseMatrix/CallPlan rule context.
     */
    fun handlePlanFinalTranscript(finalTranscript: String, priorUnknownCount: Int): CallPlanTurnResult {
        val coordinator = checkNotNull(planTurnCoordinator) { "call_plan_not_bound" }
        return coordinator.handleFinalTranscript(finalTranscript, priorUnknownCount)
    }

    /**
     * Builds bounded data for a quarantined shadow observer. The supplied snapshot and slot values
     * must already come from the deterministic application-owned path; no reducer is called here.
     */
    fun createGateDShadowObservation(
        snapshot: TaskGraphSnapshot,
        finalizedTranscript: String,
        validatedNonSecretSlots: Map<TaskGraphSlotId, TaskGraphSlotValue>,
    ): ShadowDialogueObservation =
        checkNotNull(gateDRuntime) { "gate_d_not_bound" }.createShadowObservation(
            snapshot = snapshot,
            finalizedTranscript = finalizedTranscript,
            validatedNonSecretSlots = validatedNonSecretSlots,
        )

    /** Revalidates shadow output into candidate data only; it never applies the transition. */
    fun validateGateDShadowProposal(
        observation: ShadowDialogueObservation,
        hypothesis: ShadowDialogueHypothesis,
        allowedNonSecretSlots: Set<TaskGraphSlotId>,
    ): SupervisorProposalValidation =
        checkNotNull(gateDRuntime) { "gate_d_not_bound" }.validateShadowProposal(
            observation = observation,
            hypothesis = hypothesis,
            allowedNonSecretSlots = allowedNonSecretSlots,
        )

    private fun processFinalizedTranscript(finalTranscript: String): CallPlanFinalTurnSelection {
        val turnResult = handlePlanFinalTranscript(finalTranscript)
        val selection = CallPlanFinalTurnRouteMapper.map(turnResult)
        val deterministicAction = turnResult.decision().action()
        val recoveryCount = currentRecoveryCount()
        activateGateDProductIntegration(
            finalTranscript = finalTranscript,
            turnResult = turnResult,
            recoveryCount = recoveryCount,
        )
        activateGateDShadow(
            finalTranscript = finalTranscript,
            deterministicAction = deterministicAction,
            recoveryCount = recoveryCount,
        )
        return selection
    }

    private fun activateGateDProductIntegration(
        finalTranscript: String,
        turnResult: CallPlanTurnResult,
        recoveryCount: Int,
    ) {
        try {
            val decision = turnResult.decision()
            gateDProductIntegration?.onFinalizedTurn(
                finalizedTranscript = finalTranscript,
                deterministicAction = decision.action(),
                recoveryCount = recoveryCount,
                deterministicProposal = decision.proposal(),
                deterministicPolicyDecision = turnResult.policyDecision(),
            )
        } catch (_: Throwable) {
            // Gate D application state must never alter the already-selected CallPlan route.
        }
    }

    private fun activateGateDShadow(
        finalTranscript: String,
        deterministicAction: CallPlanAction,
        recoveryCount: Int,
    ) {
        try {
            gateDShadowLifecycle?.onFinalizedTurn(
                finalizedTranscript = finalTranscript,
                deterministicAction = deterministicAction,
                recoveryCount = recoveryCount,
            )
        } catch (_: Throwable) {
            // Shadow observation is diagnostic only and must never change deterministic routing.
        }
    }

    private fun currentRecoveryCount(): Int = synchronized(planFallbackLock) {
        consecutiveUnknownCount
    }

    fun cancel() {
        gateDProductIntegration?.cancel()
        gateDShadowLifecycle?.cancel()
        pipeline.cancel()
    }

    override fun close() {
        try {
            gateDProductIntegration?.close()
        } finally {
            try {
                gateDShadowLifecycle?.close()
            } finally {
                pipeline.close()
            }
        }
    }

    companion object {
        fun create(
            context: Context,
            prepared: PreparedLocalTextCall,
            approvalPolicy: TextOutputApprovalPolicy,
            languageTag: String = "pl-PL",
        ): LocalTextCallSession = LocalTextCallSession(
            prepared = prepared,
            pipelineFactory = PipelineFactory { backend ->
                AndroidPipeline(
                    LocalSpeechTextPipeline(
                        context.applicationContext,
                        backend,
                        approvalPolicy,
                        languageTag,
                    ),
                )
            },
        )

        private fun claimPipeline(
            prepared: PreparedLocalTextCall,
            pipelineFactory: PipelineFactory,
        ): Pipeline {
            val backend = prepared.claimBackend()
            return try {
                pipelineFactory.create(backend)
            } catch (error: Throwable) {
                try { backend.close() } catch (_: Throwable) {}
                throw error
            }
        }
    }

    private class AndroidPipeline(
        private val delegate: LocalSpeechTextPipeline,
    ) : Pipeline {
        override fun start(listener: LocalSpeechTextPipeline.Listener) = delegate.start(listener)

        override fun start(
            listener: LocalSpeechTextPipeline.Listener,
            finalTurnRouteSelector: LocalSpeechFinalTurnRouteSelector,
        ) = delegate.start(listener, finalTurnRouteSelector)

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean =
            delegate.writeInputPcm(bytes, offset, length)

        override fun finishInput() = delegate.finishInput()
        override fun cancel() = delegate.cancel()
        override fun close() = delegate.close()
    }
}
