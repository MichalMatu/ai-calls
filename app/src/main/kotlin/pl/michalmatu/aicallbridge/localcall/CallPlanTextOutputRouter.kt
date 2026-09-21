package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController

/**
 * Routes one already-typed CallPlan turn result toward the existing application-owned text
 * approval boundary without owning speech, media, dialing, commitment authorization, or model
 * fallback.
 *
 * Only SAY carries speech text. Every non-speech action remains a structured result for the
 * product/session owner to handle explicitly.
 */
internal class CallPlanTextOutputRouter(
    private val controller: TextCallTurnController,
) {
    interface Listener {
        fun onApprovedText(text: String)
        fun onDroppedText()
        fun onStructuredResult(result: CallPlanTurnResult)
        fun onError(reason: String)
    }

    fun route(result: CallPlanTurnResult, listener: Listener) {
        if (result.decision().action() != CallPlanAction.SAY) {
            listener.onStructuredResult(result)
            return
        }

        val text = checkNotNull(result.decision().text()) { "call_plan_say_text_missing" }
        controller.submitCandidateText(text, object : TextCallTurnController.Listener {
            override fun onApprovedResponse(text: String) {
                listener.onApprovedText(text)
            }

            override fun onDroppedResponse() {
                listener.onDroppedText()
            }

            override fun onError(reason: String) {
                listener.onError(reason)
            }
        })
    }
}
