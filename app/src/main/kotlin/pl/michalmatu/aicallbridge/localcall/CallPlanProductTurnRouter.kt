package pl.michalmatu.aicallbridge.localcall

/**
 * Product-owned host-side composition for one already-final caller transcript.
 *
 * The coordinator remains the sole CallPlan/workflow mutation owner. The output router remains the
 * sole bridge into the existing application-owned text approval boundary. This class adds no model
 * fallback, speech/media ownership, commitment authorization, or synthesized conversational text.
 */
internal class CallPlanProductTurnRouter(
    private val coordinator: CallPlanTurnCoordinator,
    private val outputRouter: CallPlanTextOutputRouter,
) {
    fun handleFinalTranscript(
        finalTranscript: String,
        priorUnknownCount: Int,
        listener: CallPlanTextOutputRouter.Listener,
    ) {
        val result = coordinator.handleFinalTranscript(finalTranscript, priorUnknownCount)
        outputRouter.route(result, listener)
    }
}
