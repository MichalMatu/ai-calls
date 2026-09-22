package pl.michalmatu.aicallbridge.dialogue

/**
 * One non-authoritative sequence decision around a raw [DialogueFitResult].
 *
 * The raw result always records the current turn. [effective] may deliberately retain a worse
 * prior categorical fit while recovery evidence accumulates. This type carries no TaskGraph,
 * workflow, output, dialing, disclosure or commitment authority.
 */
data class DialogueFitHysteresisDecision(
    val raw: DialogueFitResult,
    val effective: DialogueFitResult,
    val heldForRecovery: Boolean,
    val recoveryEvidenceCount: Int,
)

/**
 * Small caller-owned categorical hysteresis for consecutive dialogue-fit observations.
 *
 * Safety deterioration is immediate. Improvement requires the configured number of consecutive
 * observations at the same target level; changing the target level or receiving a same/worse fit
 * resets that evidence. Callers must [reset] at session/generation boundaries.
 */
class DialogueFitHysteresis(
    private val requiredRecoveryEvidence: Int,
) {
    private var effectiveResult: DialogueFitResult? = null
    private var pendingRecoveryLevel: DialogueFitLevel? = null
    private var recoveryEvidenceCount: Int = 0

    init {
        require(requiredRecoveryEvidence > 0) { "requiredRecoveryEvidence must be positive" }
    }

    @Synchronized
    fun update(rawResult: DialogueFitResult): DialogueFitHysteresisDecision {
        val raw = rawResult.snapshot()
        val current = effectiveResult
        if (current == null) {
            effectiveResult = raw
            clearRecoveryEvidence()
            return decision(raw = raw, effective = raw, held = false)
        }

        if (severity(raw.level) >= severity(current.level)) {
            effectiveResult = raw
            clearRecoveryEvidence()
            return decision(raw = raw, effective = raw, held = false)
        }

        if (pendingRecoveryLevel == raw.level) {
            recoveryEvidenceCount += 1
        } else {
            pendingRecoveryLevel = raw.level
            recoveryEvidenceCount = 1
        }

        if (recoveryEvidenceCount >= requiredRecoveryEvidence) {
            effectiveResult = raw
            clearRecoveryEvidence()
            return decision(raw = raw, effective = raw, held = false)
        }

        return decision(raw = raw, effective = current, held = true)
    }

    @Synchronized
    fun reset() {
        effectiveResult = null
        clearRecoveryEvidence()
    }

    private fun decision(
        raw: DialogueFitResult,
        effective: DialogueFitResult,
        held: Boolean,
    ): DialogueFitHysteresisDecision = DialogueFitHysteresisDecision(
        raw = raw,
        effective = effective,
        heldForRecovery = held,
        recoveryEvidenceCount = recoveryEvidenceCount,
    )

    private fun clearRecoveryEvidence() {
        pendingRecoveryLevel = null
        recoveryEvidenceCount = 0
    }

    private fun DialogueFitResult.snapshot(): DialogueFitResult =
        copy(reasons = reasons.toList())

    private companion object {
        fun severity(level: DialogueFitLevel): Int = when (level) {
            DialogueFitLevel.HIGH -> 0
            DialogueFitLevel.UNCERTAIN -> 1
            DialogueFitLevel.LOW -> 2
            DialogueFitLevel.BROKEN -> 3
        }
    }
}
