package pl.michalmatu.aicallbridge.localcall

import android.content.Context
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
) : AutoCloseable {
    internal interface Pipeline : AutoCloseable {
        fun start(listener: LocalSpeechTextPipeline.Listener)
        fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean
        fun finishInput()
        fun cancel()
    }

    internal fun interface PipelineFactory {
        fun create(backend: TextCallAgentBackend): Pipeline
    }

    internal constructor(
        prepared: PreparedLocalTextCall,
        pipelineFactory: PipelineFactory,
    ) : this(
        pipeline = claimPipeline(prepared, pipelineFactory),
        planTurnCoordinator = prepared.callPlan?.let { CallPlanTurnCoordinator(it, prepared.workflow) },
    )

    fun start(listener: LocalSpeechTextPipeline.Listener) = pipeline.start(listener)

    fun writeInputPcm(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ): Boolean = pipeline.writeInputPcm(bytes, offset, length)

    fun finishInput() = pipeline.finishInput()

    /**
     * Routes one already-final transcript through the bound CallPlan without speaking or invoking
     * the generative backend. A plan must have been bound during readiness.
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

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean =
            delegate.writeInputPcm(bytes, offset, length)

        override fun finishInput() = delegate.finishInput()
        override fun cancel() = delegate.cancel()
        override fun close() = delegate.close()
    }
}
