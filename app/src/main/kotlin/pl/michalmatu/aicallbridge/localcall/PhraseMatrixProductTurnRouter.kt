package pl.michalmatu.aicallbridge.localcall

/**
 * One matched fast-path turn: matcher diagnostics plus the exact structured CallPlan result.
 *
 * Keeping the match separate from the CallPlan result makes latency/coverage diagnostics possible
 * without allowing matcher metadata to become workflow or output authority.
 */
internal data class PhraseMatrixProductTurnResult(
    val match: PhraseMatch,
    val turnResult: CallPlanTurnResult,
)

/**
 * Product-owned bridge from the classification-only PhraseMatrix into the existing CallPlan owner.
 *
 * A miss is explicitly unhandled so a later product layer may choose another bounded classifier or
 * supervisor. A hit contributes only its existing rule id; CallPlan validation and workflow
 * mutation remain inside [CallPlanTurnCoordinator].
 */
internal class PhraseMatrixProductTurnRouter(
    private val matrix: PhraseMatrix,
    private val coordinator: CallPlanTurnCoordinator,
) {
    fun handleFinalTranscript(
        finalTranscript: String,
        priorUnknownCount: Int,
    ): PhraseMatrixProductTurnResult? {
        require(priorUnknownCount >= 0) { "prior_unknown_count_must_be_non_negative" }
        val match = matrix.match(finalTranscript) ?: return null
        return PhraseMatrixProductTurnResult(
            match = match,
            turnResult = coordinator.handleSuggestedRuleId(match.ruleId, priorUnknownCount),
        )
    }
}
