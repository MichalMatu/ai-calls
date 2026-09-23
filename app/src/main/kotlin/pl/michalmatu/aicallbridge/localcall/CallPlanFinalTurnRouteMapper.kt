package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

internal data class CallPlanFinalTurnSelection(
    val route: TextCallFinalTurnRoute,
    val structuredResult: CallPlanTurnResult?,
)

internal enum class CallPlanUnresolvedTurnRouting {
    CONSUME,
    GENERATE_WITH_BACKEND,
}

/**
 * Maps one already-evaluated CallPlan result onto the neutral final-text dispatcher seam.
 *
 * Default behavior remains fail-closed: structured/unresolved plan results are consumed. A reviewed
 * hybrid composition may opt into backend generation only for ASK_REPEAT or TAKE_OVER. PROPOSAL and
 * COMPLETE always remain structured data and can never become model-generation triggers.
 */
internal object CallPlanFinalTurnRouteMapper {
    fun map(
        result: CallPlanTurnResult,
        unresolvedRouting: CallPlanUnresolvedTurnRouting = CallPlanUnresolvedTurnRouting.CONSUME,
    ): CallPlanFinalTurnSelection = when (result.decision().action()) {
        CallPlanAction.SAY -> CallPlanFinalTurnSelection(
            route = TextCallFinalTurnRoute.Candidate(
                checkNotNull(result.decision().text()) { "call_plan_say_text_missing" },
            ),
            structuredResult = null,
        )

        CallPlanAction.ASK_REPEAT,
        CallPlanAction.TAKE_OVER,
        -> CallPlanFinalTurnSelection(
            route = if (unresolvedRouting == CallPlanUnresolvedTurnRouting.GENERATE_WITH_BACKEND) {
                TextCallFinalTurnRoute.Generate
            } else {
                TextCallFinalTurnRoute.Consumed
            },
            structuredResult = result,
        )

        CallPlanAction.PROPOSAL,
        CallPlanAction.COMPLETE,
        -> CallPlanFinalTurnSelection(
            route = TextCallFinalTurnRoute.Consumed,
            structuredResult = result,
        )
    }
}
