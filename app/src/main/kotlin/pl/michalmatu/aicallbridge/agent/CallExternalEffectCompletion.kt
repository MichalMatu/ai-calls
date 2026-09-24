package pl.michalmatu.aicallbridge.agent

/**
 * Redacted application-owned evidence that the already-consumed external effect succeeded outside
 * the app. Construction alone grants no authority; reviewed product code decides when such factual
 * evidence exists.
 */
class CallExternalEffectSuccessEvidence(
    val effect: CallExternalEffect,
) {
    override fun toString(): String =
        "CallExternalEffectSuccessEvidence(effect=REDACTED)"
}

/**
 * Post-consumption completion tracker for one exact external effect.
 *
 * This is deliberately not a second authorization store. It can only be created from commitment
 * consumption evidence, after the one-shot permit has already disappeared from [CallCommitmentGate].
 * Completion requires separate exact external-success evidence and can happen at most once.
 */
class CallExternalEffectCompletionTracker private constructor(
    private val consumedEffect: CallExternalEffect,
) {
    private val lock = Any()
    private var completed = false

    fun complete(successEvidence: CallExternalEffectSuccessEvidence): Result<CallExternalEffect> =
        synchronized(lock) {
            if (completed) {
                return@synchronized Result.failure(
                    IllegalStateException("external effect is already completed"),
                )
            }
            if (successEvidence.effect != consumedEffect) {
                return@synchronized Result.failure(
                    IllegalStateException("external success evidence does not match consumed effect"),
                )
            }
            completed = true
            Result.success(consumedEffect)
        }

    fun isCompleted(): Boolean = synchronized(lock) { completed }

    companion object {
        fun fromConsumption(
            evidence: CallCommitmentConsumptionEvidence,
        ): CallExternalEffectCompletionTracker =
            CallExternalEffectCompletionTracker(evidence.effect)
    }
}
