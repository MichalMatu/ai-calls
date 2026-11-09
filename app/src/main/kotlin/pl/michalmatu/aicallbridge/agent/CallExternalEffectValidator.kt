package pl.michalmatu.aicallbridge.agent

/** Deterministic application-owned validation result for one typed external-effect candidate. */
sealed interface CallExternalEffectValidation {
    data class Accepted(
        val effect: CallExternalEffect,
    ) : CallExternalEffectValidation

    data class Rejected(
        val reason: RejectReason,
    ) : CallExternalEffectValidation

    enum class RejectReason {
        TASK_ACTION_MISMATCH,
        TASK_SERVICE_MISMATCH,
        TARGET_MISMATCH,
        MISSING_EXPLICIT_SERVICE_VALUE,
        SERVICE_VALUE_MISMATCH,
        PROPOSAL_MISMATCH,
        APPOINTMENT_TIME_MISSING,
        HARD_CONSTRAINT_VIOLATION,
        USER_DECISION_REQUIRED,
    }
}

/**
 * Deterministic validator binding typed external effects back to user-authorized [CallTask] scope
 * and the exact [CallResolvedTarget]. Models and supervisor output never call this with authority of
 * their own; they may only supply candidate data for the application to validate.
 */
object CallExternalEffectValidator {
    const val SERVICE_ENABLED_FACT = "service.enabled"
    private const val SET_SERVICE_ACTION = "SET_SERVICE"

    /**
     * Validate one concrete booking effect before consuming its one-shot permit.
     * [reviewedProposal] and [userApprovedSoftDeviation] originate from the app-owned
     * workflow, never from model text. Target/Call ownership is enforced by that workflow.
     * No provider reservation is performed here.
     */
    fun validateBookAppointment(
        task: CallTask,
        reviewedProposal: CallProposal,
        candidate: CallExternalEffect.BookAppointment,
        userApprovedSoftDeviation: Boolean,
    ): CallExternalEffectValidation {
        val action = task.action().trim().replace('_', ' ').replace(Regex("\\s+"), " ")
        if (!action.equals("book appointment", ignoreCase = true)) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.TASK_ACTION_MISMATCH,
            )
        }
        if (candidate.proposal != reviewedProposal) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.PROPOSAL_MISMATCH,
            )
        }
        if (candidate.proposal.scheduledAt() == null) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.APPOINTMENT_TIME_MISSING,
            )
        }

        val decision = CallConfirmationPolicy().evaluate(task, candidate.proposal)
        if (decision.reasons().any { it in HARD_BOOKING_REASONS }) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.HARD_CONSTRAINT_VIOLATION,
            )
        }
        if (decision.action() == CallPolicyAction.NEEDS_USER_DECISION &&
            !userApprovedSoftDeviation) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.USER_DECISION_REQUIRED,
            )
        }
        return CallExternalEffectValidation.Accepted(candidate)
    }

    private val HARD_BOOKING_REASONS = setOf(
        CallPolicyReason.TIME_UNKNOWN,
        CallPolicyReason.TIME_OUTSIDE_ALLOWED,
        CallPolicyReason.PRICE_UNKNOWN,
        CallPolicyReason.PRICE_CURRENCY_MISMATCH,
        CallPolicyReason.PRICE_ABOVE_MAX,
        CallPolicyReason.PAYMENT_MODE_UNKNOWN,
        CallPolicyReason.PAYMENT_MODE_NOT_ALLOWED,
    )

    fun validateSetService(
        task: CallTask,
        resolvedTarget: CallResolvedTarget,
        candidate: CallExternalEffect.SetService,
    ): CallExternalEffectValidation {
        if (!task.action().equals(SET_SERVICE_ACTION, ignoreCase = true)) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.TASK_ACTION_MISMATCH,
            )
        }
        if (!task.service().equals(candidate.service.name, ignoreCase = true)) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.TASK_SERVICE_MISMATCH,
            )
        }
        if (candidate.target != resolvedTarget) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.TARGET_MISMATCH,
            )
        }

        val expectedEnabled = task.authorizedFacts()[SERVICE_ENABLED_FACT]
            ?.lowercase()
            ?.toBooleanStrictOrNull()
            ?: return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.MISSING_EXPLICIT_SERVICE_VALUE,
            )
        if (candidate.enabled != expectedEnabled) {
            return CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.SERVICE_VALUE_MISMATCH,
            )
        }

        // SET_SERVICE currently carries no price/time/provider material terms. Task constraints stay
        // available for adapters whose typed effect has such terms; they are not silently widened.
        return CallExternalEffectValidation.Accepted(candidate)
    }
}
