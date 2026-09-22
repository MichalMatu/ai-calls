package pl.michalmatu.aicallbridge.dialogue

import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

enum class SupervisorProposalRejectReason {
    STALE_GENERATION,
    MISSING_TRANSITION,
    TRANSITION_NOT_ALLOWED,
    SLOT_NOT_ALLOWED,
    AUTHORITY_BEARING_SLOT,
    LOW_CONFIDENCE,
}

/**
 * Revalidated supervisor output that is still only candidate data.
 *
 * This type deliberately has no TaskGraph reducer/event conversion, workflow, speech, dialing or
 * commitment API. A product owner must perform the next application-owned validation step.
 */
class ValidatedSupervisorCandidate(
    val generation: Long,
    val transitionId: TaskGraphTransitionId,
    slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue>,
    val confidence: Double,
) {
    val slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = slotCandidates.toMap()

    init {
        require(generation >= 0L) { "generation must be non-negative" }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "confidence must be finite and between zero and one"
        }
    }

    override fun toString(): String =
        "ValidatedSupervisorCandidate(" +
            "generation=$generation, " +
            "transitionId=$transitionId, " +
            "slotCandidateIds=${slotCandidates.keys}, " +
            "confidence=$confidence)"
}

sealed interface SupervisorProposalValidation {
    data class Accepted(val candidate: ValidatedSupervisorCandidate) : SupervisorProposalValidation

    data class Rejected(val reason: SupervisorProposalRejectReason) : SupervisorProposalValidation
}

/**
 * Application-owned fail-closed boundary between a quarantined shadow hypothesis and later
 * TaskGraph validation. It never executes the suggested transition.
 */
class SupervisorProposalValidator(
    private val minimumConfidence: Double = 0.80,
) {
    init {
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0) {
            "minimumConfidence must be finite and between zero and one"
        }
    }

    fun validate(
        observation: ShadowDialogueObservation,
        hypothesis: ShadowDialogueHypothesis,
        allowedNonSecretSlots: Set<TaskGraphSlotId>,
    ): SupervisorProposalValidation {
        if (hypothesis.generation != observation.generation) {
            return rejected(SupervisorProposalRejectReason.STALE_GENERATION)
        }

        val transitionId = hypothesis.suggestedTransition
            ?: return rejected(SupervisorProposalRejectReason.MISSING_TRANSITION)
        if (transitionId !in observation.allowedTransitions) {
            return rejected(SupervisorProposalRejectReason.TRANSITION_NOT_ALLOWED)
        }

        if (hypothesis.confidence < minimumConfidence) {
            return rejected(SupervisorProposalRejectReason.LOW_CONFIDENCE)
        }

        if (hypothesis.slotCandidates.keys.any(::isAuthorityBearingSlot)) {
            return rejected(SupervisorProposalRejectReason.AUTHORITY_BEARING_SLOT)
        }

        val allowedSlots = allowedNonSecretSlots.toSet()
        if (hypothesis.slotCandidates.keys.any { it !in allowedSlots }) {
            return rejected(SupervisorProposalRejectReason.SLOT_NOT_ALLOWED)
        }

        return SupervisorProposalValidation.Accepted(
            ValidatedSupervisorCandidate(
                generation = observation.generation,
                transitionId = transitionId,
                slotCandidates = hypothesis.slotCandidates,
                confidence = hypothesis.confidence,
            ),
        )
    }

    private fun rejected(reason: SupervisorProposalRejectReason): SupervisorProposalValidation =
        SupervisorProposalValidation.Rejected(reason)

    private fun isAuthorityBearingSlot(id: TaskGraphSlotId): Boolean =
        normalize(id.value) in AUTHORITY_BEARING_SLOT_IDS

    private companion object {
        val AUTHORITY_BEARING_SLOT_IDS = setOf(
            "SPEECH",
            "UTTERANCE",
            "TTS",
            "DIAL",
            "TARGET",
            "ACTION",
            "ACTION_ID",
            "COMMIT",
            "COMMITMENT",
            "AUTHORIZATION",
            "IDENTITY",
            "IDENTITY_VALUE",
            "FACT_VALUE",
            "CREDENTIAL",
            "CREDENTIALS",
        )

        fun normalize(value: String): String = value.trim().uppercase()
    }
}
