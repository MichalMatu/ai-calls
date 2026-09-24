package pl.michalmatu.aicallbridge.agent

/** Service identifiers currently supported by typed external-effect authority. */
enum class CallService {
    CLIR,
}

/**
 * Application-owned typed description of one concrete external effect that a call may commit.
 *
 * This is authority data, not model intent. A language model may help discover or phrase an effect,
 * but only application policy/user approval may authorize one exact value through
 * [CallCommitmentGate].
 */
sealed interface CallExternalEffect {
    /**
     * Compatibility effect for the already-proven BOOK_APPOINTMENT path.
     *
     * The existing [CallProposal] remains the workflow proposal model for now; wrapping it here
     * separates commitment authority from appointment-specific proposal storage without creating a
     * second authorization store.
     */
    data class BookAppointment(
        val proposal: CallProposal,
    ) : CallExternalEffect {
        override fun toString(): String =
            "CallExternalEffect.BookAppointment(proposal=REDACTED)"
    }

    /** Exact service-setting effect bound to one already-resolved dial target. */
    data class SetService(
        val target: CallResolvedTarget,
        val service: CallService,
        val enabled: Boolean,
    ) : CallExternalEffect {
        override fun toString(): String =
            "CallExternalEffect.SetService(data=REDACTED)"
    }
}
