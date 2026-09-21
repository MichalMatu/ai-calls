package pl.michalmatu.aicallbridge.textagent

/**
 * Neutral product text-turn seam between one final transcript and the existing text controller.
 *
 * The dispatcher knows nothing about CallPlan, workflow, speech, media, dialing, or commitment
 * authority. The product owner chooses exactly one route for each final transcript:
 *
 * - [TextCallFinalTurnRoute.Generate] -> ordinary backend generation;
 * - [TextCallFinalTurnRoute.Candidate] -> exact already-determined text through existing approval;
 * - [TextCallFinalTurnRoute.Consumed] -> no text output; invalidate stale controller work.
 */
internal class TextCallFinalTurnDispatcher(
    private val controller: TextCallTurnController,
) {
    interface Listener {
        fun onApprovedText(text: String)
        fun onDroppedText()
        fun onConsumed()
        fun onError(reason: String)
    }

    fun dispatch(
        finalTranscript: String,
        route: TextCallFinalTurnRoute,
        listener: Listener,
    ) {
        require(finalTranscript.isNotBlank()) { "final_transcript_must_not_be_blank" }
        when (route) {
            TextCallFinalTurnRoute.Generate ->
                controller.submitUserText(finalTranscript, controllerListener(listener))

            is TextCallFinalTurnRoute.Candidate ->
                controller.submitCandidateText(route.text, controllerListener(listener))

            TextCallFinalTurnRoute.Consumed -> {
                controller.cancel()
                listener.onConsumed()
            }
        }
    }

    private fun controllerListener(listener: Listener) = object : TextCallTurnController.Listener {
        override fun onApprovedResponse(text: String) = listener.onApprovedText(text)
        override fun onDroppedResponse() = listener.onDroppedText()
        override fun onError(reason: String) = listener.onError(reason)
    }
}

internal sealed interface TextCallFinalTurnRoute {
    data object Generate : TextCallFinalTurnRoute

    data class Candidate(val text: String) : TextCallFinalTurnRoute {
        init {
            require(text.isNotBlank()) { "candidate_text_must_not_be_blank" }
        }
    }

    data object Consumed : TextCallFinalTurnRoute
}
