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
