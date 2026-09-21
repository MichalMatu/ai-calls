package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

internal data class CallPlanFinalTurnSelection(
    val route: TextCallFinalTurnRoute,
    val structuredResult: CallPlanTurnResult?,
)

/** Maps one already-evaluated CallPlan result onto the neutral final-text dispatcher seam. */
internal object CallPlanFinalTurnRouteMapper {
    fun map(result: CallPlanTurnResult): CallPlanFinalTurnSelection =
        when (result.decision().action()) {
            CallPlanAction.SAY -> CallPlanFinalTurnSelection(
                route = TextCallFinalTurnRoute.Candidate(
                    checkNotNull(result.decision().text()) { "call_plan_say_text_missing" },
                ),
                structuredResult = null,
            )

            CallPlanAction.ASK_REPEAT,
            CallPlanAction.PROPOSAL,
            CallPlanAction.COMPLETE,
            CallPlanAction.TAKE_OVER,
            -> CallPlanFinalTurnSelection(
                route = TextCallFinalTurnRoute.Consumed,
                structuredResult = result,
            )
        }
}
