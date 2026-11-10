package pl.michalmatu.aicallbridge.incoming

/** The observed end of a Call alone does not prove that the AI end_call caused it. */
internal enum class IncomingEndToolOutcome { PENDING, CONFIRMED, UNKNOWN }

internal object IncomingEndToolEvidencePolicy {
    fun decide(
        callDisconnected: Boolean,
        endingOwnerAtDisconnect: Boolean,
    ): IncomingEndToolOutcome = when {
        !callDisconnected -> if (endingOwnerAtDisconnect) IncomingEndToolOutcome.PENDING
                             else IncomingEndToolOutcome.UNKNOWN
        endingOwnerAtDisconnect -> IncomingEndToolOutcome.CONFIRMED
        else -> IncomingEndToolOutcome.UNKNOWN
    }
}
