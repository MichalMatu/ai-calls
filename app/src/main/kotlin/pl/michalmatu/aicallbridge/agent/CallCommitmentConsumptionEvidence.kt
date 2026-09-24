package pl.michalmatu.aicallbridge.agent

/**
 * Redacted application-owned evidence that one exact effect-bound commitment permit was consumed.
 *
 * Consumption is not business success and grants no completion authority. The permit itself is
 * intentionally absent so this evidence cannot be replayed as commitment authorization.
 */
class CallCommitmentConsumptionEvidence(
    val effect: CallExternalEffect,
) {
    /** BOOK_APPOINTMENT compatibility adapter for existing reviewed product wiring. */
    constructor(proposal: CallProposal) : this(CallExternalEffect.BookAppointment(proposal))

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
