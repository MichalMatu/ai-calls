package pl.michalmatu.aicallbridge.localcall

/**
 * One matched fast-path turn: matcher/temperature diagnostics plus the exact structured CallPlan
 * result. Keeping classification evidence separate from the CallPlan result makes latency/coverage
 * diagnostics possible without allowing matcher metadata to become workflow or output authority.
 */
internal data class PhraseMatrixProductTurnResult(
    val match: PhraseMatch?,
    val temperature: PhraseResponseTemperature?,
    val turnResult: CallPlanTurnResult,
)

/**
 * Product-owned bridge from the classification-only PhraseMatrix into the existing CallPlan owner.
 *
 * Exact/alias/fuzzy matches remain first. A unique WARM response-temperature assessment may suggest
 * the same existing rule id when natural wording drift prevented a strict match. UNCERTAIN, COLD and
 * AMBIGUOUS assessments remain explicitly unhandled so a later product layer may choose a bounded
 * model/supervisor fallback. In every handled case CallPlan remains the validation/workflow owner.
 */
internal class PhraseMatrixProductTurnRouter(
    private val matrix: PhraseMatrix,
    private val coordinator: CallPlanTurnCoordinator,
) {
    fun handleFinalTranscript(
        finalTranscript: String,
        priorUnknownCount: Int,
        previousRuleId: String? = null,
    ): PhraseMatrixProductTurnResult? {
        require(priorUnknownCount >= 0) { "prior_unknown_count_must_be_non_negative" }

        val strictMatch = matrix.match(finalTranscript, previousRuleId)
        if (strictMatch != null) {
            return PhraseMatrixProductTurnResult(
                match = strictMatch,
                temperature = null,
                turnResult = coordinator.handleSuggestedRuleId(strictMatch.ruleId, priorUnknownCount),
            )
        }

        val temperature = matrix.assessResponseTemperature(finalTranscript, previousRuleId)
        if (temperature.band != PhraseResponseTemperatureBand.WARM) return null
        val ruleId = checkNotNull(temperature.ruleId) { "warm_temperature_rule_missing" }
        return PhraseMatrixProductTurnResult(
            match = null,
            temperature = temperature,
            turnResult = coordinator.handleSuggestedRuleId(ruleId, priorUnknownCount),
        )
    }
}
