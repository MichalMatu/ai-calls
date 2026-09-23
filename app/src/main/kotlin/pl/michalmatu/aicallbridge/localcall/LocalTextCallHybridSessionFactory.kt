package pl.michalmatu.aicallbridge.localcall

import android.content.Context
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy

/**
 * Explicit reviewed Android composition that opts unresolved CallPlan turns into backend generation.
 * The default LocalTextCallSession.create path remains fail-closed/consumed.
 */
internal object LocalTextCallHybridSessionFactory {
    fun create(
        context: Context,
        prepared: PreparedLocalTextCall,
        approvalPolicy: TextOutputApprovalPolicy,
        languageTag: String = "pl-PL",
    ): LocalTextCallSession = LocalTextCallSession(
        prepared = prepared,
        pipelineFactory = LocalTextCallSession.PipelineFactory { backend ->
            HybridAndroidPipeline(
                LocalSpeechTextPipeline(
                    context.applicationContext,
                    backend,
                    approvalPolicy,
                    languageTag,
                ),
            )
        },
        unresolvedTurnRouting = CallPlanUnresolvedTurnRouting.GENERATE_WITH_BACKEND,
    )

    private class HybridAndroidPipeline(
        private val delegate: LocalSpeechTextPipeline,
    ) : LocalTextCallSession.Pipeline {
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
