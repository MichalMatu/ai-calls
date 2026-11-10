package pl.michalmatu.aicallbridge.agent

/**
 * Redacted application-owned evidence that one exact effect-bound commitment permit was consumed.
 *
 * Consumption is not business success and grants no completion authority. The permit itself is
 * intentionally absent so this evidence cannot be replayed as commitment authorization.
 */
class CallCommitmentConsumptionEvidence private constructor(
    val effect: CallExternalEffect,
) {
    // A model, public caller or unconsumed proposal cannot construct this receipt.
    // Trusted application code obtains it from CallCommitmentGate.consumeEvidence.
    internal companion object {
        fun issuedAfterGateConsumption(effect: CallExternalEffect): CallCommitmentConsumptionEvidence =
            CallCommitmentConsumptionEvidence(effect)
    }

    /**
     * BOOK_APPOINTMENT compatibility view.
     *
     * New generic code should inspect [effect]. Access from a non-appointment effect fails closed.
     */
    val proposal: CallProposal
        get() = (effect as? CallExternalEffect.BookAppointment)?.proposal
            ?: error("commitment consumption evidence is not a BOOK_APPOINTMENT effect")

    override fun toString(): String =
        "CallCommitmentConsumptionEvidence(effect=REDACTED)"
}

/** Receives evidence only after the one-shot commitment permit was atomically consumed. */
fun interface CallCommitmentConsumptionListener {
    fun onConsumed(evidence: CallCommitmentConsumptionEvidence)
}
