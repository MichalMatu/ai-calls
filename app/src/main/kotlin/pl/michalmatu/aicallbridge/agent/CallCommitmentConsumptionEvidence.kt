package pl.michalmatu.aicallbridge.agent

/**
 * Redacted application-owned evidence that one exact proposal-bound commitment permit was consumed.
 *
 * Consumption is not business success and grants no completion authority. The permit itself is
 * intentionally absent so this evidence cannot be replayed as commitment authorization.
 */
class CallCommitmentConsumptionEvidence(
    val proposal: CallProposal,
) {
    override fun toString(): String =
        "CallCommitmentConsumptionEvidence(proposal=REDACTED)"
}

/** Receives evidence only after the one-shot commitment permit was atomically consumed. */
fun interface CallCommitmentConsumptionListener {
    fun onConsumed(evidence: CallCommitmentConsumptionEvidence)
}
