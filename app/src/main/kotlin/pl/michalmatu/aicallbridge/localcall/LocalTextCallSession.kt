package pl.michalmatu.aicallbridge.localcall

import android.content.Context
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueHypothesis
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObservation
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObserver
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalValidation
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
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
 * output directly, authorize commitment, approve proposals, or fall back to a backend/model.
 * Optional Gate D context remains shadow-only: finalized plan turns may create one bounded
 * observation for an explicitly host-bound observer, but this session never reduces TaskGraph
 * events or executes supervisor candidates.
 */
internal class LocalTextCallSession private constructor(
    private val pipeline: Pipeline,
    private val planTurnCoordinator: CallPlanTurnCoordinator?,
    phraseMatrix: PhraseMatrix?,
    private val gateDRuntime: LocalTextCallGateDRuntime?,
    gateDSnapshot: TaskGraphSnapshot?,
    gateDMaxRecoveryCount: Int,
    gateDShadowDependencies: GateDShadowDependencies?,
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

    private val planFallbackLock = Any()
    private var consecutiveUnknownCount = 0
    private var previousValidatedRuleId: String? = null

    private constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
        gateDShadowDependencies: GateDShadowDependencies?,
    ) : this(
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
        gateDSnapshot = prepared.taskGraph?.initialSnapshot(),
        gateDMaxRecoveryCount = prepared.taskGraph?.maxRecoveryCount ?: 0,
        gateDShadowDependencies = gateDShadowDependencies,
    )

    internal constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
    ) : this(
        prepared = prepared,
        pipelineFactory = pipelineFactory,
        gateDShadowDependencies = null,
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
                val turnResult = handlePlanFinalTranscript(finalTranscript)
                val selection = CallPlanFinalTurnRouteMapper.map(turnResult)
                selection.structuredResult?.let(planTurnListener::onStructuredResult)
                activateGateDShadow(
                    finalTranscript = finalTranscript,
                    deterministicAction = turnResult.decision().action(),
                    recoveryCount = currentRecoveryCount(),
                )
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
        gateDShadowLifecycle?.cancel()
        pipeline.cancel()
    }

    override fun close() {
        try {
            gateDShadowLifecycle?.close()
        } finally {
            pipeline.close()
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
