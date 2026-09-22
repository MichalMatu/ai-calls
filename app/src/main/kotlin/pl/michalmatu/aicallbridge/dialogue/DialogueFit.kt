package pl.michalmatu.aicallbridge.dialogue

import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

enum class DialogueSttQuality {
    GOOD,
    UNCERTAIN,
    UNAVAILABLE,
}

enum class DeterministicUnderstanding {
    MATCHED,
    UNKNOWN,
}

enum class ParserCompleteness {
    COMPLETE,
    PARTIAL,
    NOT_APPLICABLE,
}

enum class StateCompatibility {
    COMPATIBLE,
    INCOMPATIBLE,
}

enum class ShadowComparison {
    NOT_AVAILABLE,
    AGREES,
    DISAGREES,
}

enum class DialogueFitLevel {
    HIGH,
    UNCERTAIN,
    LOW,
    BROKEN,
}

enum class DialogueFitReason {
    STT_UNCERTAIN,
    DETERMINISTIC_UNKNOWN,
    PARSER_INCOMPLETE,
    STATE_INCOMPATIBLE,
    CONTRADICTION,
    MISSING_REQUIRED_SLOTS,
    REPEATED_RECOVERY,
    RECOVERY_EXHAUSTED,
    SHADOW_DISAGREEMENT,
}

data class DialogueFitSignals(
    val sttQuality: DialogueSttQuality,
    val deterministicUnderstanding: DeterministicUnderstanding,
    val parserCompleteness: ParserCompleteness,
    val stateCompatibility: StateCompatibility,
    val contradictionDetected: Boolean,
    val missingRequiredSlots: Int,
    val recoveryCount: Int,
    val shadowComparison: ShadowComparison,
) {
    init {
        require(missingRequiredSlots >= 0) { "missingRequiredSlots must be non-negative" }
        require(recoveryCount >= 0) { "recoveryCount must be non-negative" }
    }
}

data class DialogueFitResult(
    val level: DialogueFitLevel,
    val reasons: List<DialogueFitReason>,
)

/**
 * Application-owned categorical escalation policy.
 *
 * It combines deterministic signals only. It neither executes a transition nor interprets model
 * output as authority. Numeric thresholds are intentionally absent from this first contract.
 */
class DefaultDialogueFitPolicy(
    private val maxRecoveryCount: Int = 2,
) {
    init {
        require(maxRecoveryCount > 0) { "maxRecoveryCount must be positive" }
    }

    fun evaluate(signals: DialogueFitSignals): DialogueFitResult {
        val reasons = linkedSetOf<DialogueFitReason>()

        if (signals.sttQuality == DialogueSttQuality.UNCERTAIN) {
            reasons += DialogueFitReason.STT_UNCERTAIN
        }
        if (signals.deterministicUnderstanding == DeterministicUnderstanding.UNKNOWN) {
            reasons += DialogueFitReason.DETERMINISTIC_UNKNOWN
        }
        if (signals.parserCompleteness == ParserCompleteness.PARTIAL) {
            reasons += DialogueFitReason.PARSER_INCOMPLETE
        }
        if (signals.stateCompatibility == StateCompatibility.INCOMPATIBLE) {
            reasons += DialogueFitReason.STATE_INCOMPATIBLE
        }
        if (signals.contradictionDetected) {
            reasons += DialogueFitReason.CONTRADICTION
        }
        if (signals.missingRequiredSlots > 0) {
            reasons += DialogueFitReason.MISSING_REQUIRED_SLOTS
        }
        if (signals.recoveryCount > 0) {
            reasons += DialogueFitReason.REPEATED_RECOVERY
        }
        if (signals.recoveryCount >= maxRecoveryCount) {
            reasons += DialogueFitReason.RECOVERY_EXHAUSTED
        }
        if (signals.shadowComparison == ShadowComparison.DISAGREES) {
            reasons += DialogueFitReason.SHADOW_DISAGREEMENT
        }

        val broken = signals.recoveryCount >= maxRecoveryCount &&
            (
                signals.contradictionDetected ||
                    signals.stateCompatibility == StateCompatibility.INCOMPATIBLE ||
                    signals.deterministicUnderstanding == DeterministicUnderstanding.UNKNOWN
                )

        val low = signals.contradictionDetected ||
            signals.stateCompatibility == StateCompatibility.INCOMPATIBLE ||
            signals.shadowComparison == ShadowComparison.DISAGREES ||
            (
                signals.deterministicUnderstanding == DeterministicUnderstanding.UNKNOWN &&
                    signals.recoveryCount > 0
                ) ||
            signals.recoveryCount >= maxRecoveryCount

        val level = when {
            broken -> DialogueFitLevel.BROKEN
            low -> DialogueFitLevel.LOW
            reasons.isNotEmpty() -> DialogueFitLevel.UNCERTAIN
            else -> DialogueFitLevel.HIGH
        }
        return DialogueFitResult(level, reasons.toList())
    }
}

/**
 * Bounded non-authoritative context for a finalized counterparty turn.
 *
 * Identity data is represented only by field IDs. The transcript is deliberately redacted from
 * [toString] so ordinary diagnostics do not copy dialogue text into logs by accident.
 */
class ShadowDialogueObservation(
    val generation: Long,
    taskGoal: String,
    val state: TaskGraphStateId,
    allowedTransitions: Set<TaskGraphTransitionId>,
    validatedSlots: Map<TaskGraphSlotId, TaskGraphSlotValue>,
    availableFacts: Set<IdentityFieldId>,
    val finalizedTranscript: String,
) {
    val taskGoal: String = taskGoal.trim()
    val allowedTransitions: Set<TaskGraphTransitionId> = allowedTransitions.toSet()
    val validatedSlots: Map<TaskGraphSlotId, TaskGraphSlotValue> = validatedSlots.toMap()
    val availableFacts: Set<IdentityFieldId> = availableFacts.toSet()

    init {
        require(generation >= 0L) { "generation must be non-negative" }
        require(this.taskGoal.isNotBlank()) { "taskGoal must not be blank" }
        require(finalizedTranscript.isNotBlank()) { "finalizedTranscript must not be blank" }
    }

    override fun toString(): String =
        "ShadowDialogueObservation(" +
            "generation=$generation, " +
            "state=$state, " +
            "allowedTransitions=$allowedTransitions, " +
            "validatedSlotIds=${validatedSlots.keys}, " +
            "availableFacts=$availableFacts, " +
            "taskGoal=REDACTED, finalizedTranscript=REDACTED)"
}

/**
 * Quarantined shadow interpretation. It is data only and deliberately has no reducer/workflow API.
 */
class ShadowDialogueHypothesis(
    val generation: Long,
    val suggestedTransition: TaskGraphTransitionId?,
    slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue>,
    val confidence: Double,
    diagnostics: Map<String, String> = emptyMap(),
) {
    val slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = slotCandidates.toMap()
    val diagnostics: Map<String, String> = diagnostics.toMap()

    init {
        require(generation >= 0L) { "generation must be non-negative" }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "confidence must be finite and between zero and one"
        }
        val unsafeKey = this.diagnostics.keys.firstOrNull { normalizeKey(it) in AUTHORITY_KEYS }
        require(unsafeKey == null) { "shadow diagnostics contain an authority-bearing key" }
    }

    override fun toString(): String =
        "ShadowDialogueHypothesis(" +
            "generation=$generation, " +
            "suggestedTransition=$suggestedTransition, " +
            "slotCandidateIds=${slotCandidates.keys}, " +
            "confidence=$confidence, diagnosticsKeys=${diagnostics.keys})"

    private companion object {
        val AUTHORITY_KEYS = setOf(
            "speech",
            "utterance",
            "tts",
            "dial",
            "target",
            "action",
            "action_id",
            "commit",
            "commitment",
            "authorization",
            "identity",
            "identity_value",
            "fact_value",
            "credential",
            "credentials",
        )

        fun normalizeKey(value: String): String = value.trim().lowercase()
    }
}

fun interface ShadowDialogueObserver {
    fun observe(observation: ShadowDialogueObservation): ShadowDialogueHypothesis
}
