package pl.michalmatu.aicallbridge.localcall

import android.content.Context
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy

/**
 * Narrow product-owned local dialogue session. Telephony media ownership and endpointing stay
 * outside this class; the session owns the already-proven backend/speech pipeline plus an optional
 * deterministic CallPlan turn coordinator carried by the prepared call.
 *
 * CallPlan routing remains structured only here: this class does not synthesize or transmit plan
 * output directly, authorize commitment, approve proposals, or fall back to a backend/model.
 */
internal class LocalTextCallSession private constructor(
    private val pipeline: Pipeline,
    private val planTurnCoordinator: CallPlanTurnCoordinator?,
    phraseMatrix: PhraseMatrix?,
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

    private val phraseMatrixTurnRouter: PhraseMatrixProductTurnRouter? =
        if (phraseMatrix != null) {
            PhraseMatrixProductTurnRouter(
                phraseMatrix,
                checkNotNull(planTurnCoordinator) { "phrase_matrix_requires_call_plan" },
            )
        } else {
            null
        }

    private val planFallbackLock = Any()
    private var consecutiveUnknownCount = 0
    private var previousValidatedRuleId: String? = null

    internal constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
    ) : this(
        pipeline = claimPipeline(prepared, pipelineFactory),
        planTurnCoordinator = prepared.callPlan?.let { CallPlanTurnCoordinator(it, prepared.workflow) },
        phraseMatrix = prepared.phraseMatrix,
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
                val selection = CallPlanFinalTurnRouteMapper.map(
                    handlePlanFinalTranscript(finalTranscript),
                )
                selection.structuredResult?.let(planTurnListener::onStructuredResult)
                selection.route
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

    fun cancel() = pipeline.cancel()

    override fun close() = pipeline.close()

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
