package pl.michalmatu.aicallbridge.taskgraph

import pl.michalmatu.aicallbridge.dialogue.ValidatedSupervisorCandidate

enum class TaskGraphCandidateProvenance {
    DETERMINISTIC,
    SUPERVISOR,
}

/**
 * Candidate data entering the application-owned TaskGraph apply boundary.
 *
 * This type carries no execution authority. In particular it owns no workflow, speech, dialing,
 * identity-disclosure or commitment API.
 */
class TaskGraphApplyCandidate private constructor(
    val generation: Long,
    val transitionId: TaskGraphTransitionId,
    slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue>,
    val provenance: TaskGraphCandidateProvenance,
) {
    val slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = slotCandidates.toMap()

    init {
        require(generation >= 0L) { "generation must be non-negative" }
    }

    override fun toString(): String =
        "TaskGraphApplyCandidate(" +
            "generation=$generation, " +
            "transitionId=$transitionId, " +
            "slotCandidateIds=${slotCandidates.keys}, " +
            "provenance=$provenance)"

    companion object {
        fun fromSupervisor(candidate: ValidatedSupervisorCandidate): TaskGraphApplyCandidate =
            TaskGraphApplyCandidate(
                generation = candidate.generation,
                transitionId = candidate.transitionId,
                slotCandidates = candidate.slotCandidates,
                provenance = TaskGraphCandidateProvenance.SUPERVISOR,
            )

        fun deterministic(
            generation: Long,
            transitionId: TaskGraphTransitionId,
            slotCandidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
        ): TaskGraphApplyCandidate = TaskGraphApplyCandidate(
            generation = generation,
            transitionId = transitionId,
            slotCandidates = slotCandidates,
            provenance = TaskGraphCandidateProvenance.DETERMINISTIC,
        )
    }
}

sealed interface TaskGraphApplySlotSchema {
    fun accepts(value: TaskGraphSlotValue): Boolean

    class Text(
        private val validate: (String) -> Boolean = { true },
    ) : TaskGraphApplySlotSchema {
        override fun accepts(value: TaskGraphSlotValue): Boolean =
            value is TaskGraphSlotValue.Text && validate(value.value)
    }
}

data class TaskGraphApplySlotRule(
    val required: Boolean = false,
    val schema: TaskGraphApplySlotSchema,
)

class TaskGraphApplyRule(
    val transitionId: TaskGraphTransitionId,
    val eventId: TaskGraphEventId,
    allowedProvenance: Set<TaskGraphCandidateProvenance>,
    slotRules: Map<TaskGraphSlotId, TaskGraphApplySlotRule> = emptyMap(),
) {
    val allowedProvenance: Set<TaskGraphCandidateProvenance> = allowedProvenance.toSet()
    val slotRules: Map<TaskGraphSlotId, TaskGraphApplySlotRule> = slotRules.toMap()

    init {
        require(this.allowedProvenance.isNotEmpty()) { "allowed provenance must not be empty" }
    }
}

class TaskGraphApplyPolicy(rules: List<TaskGraphApplyRule>) {
    private val rulesByTransitionId: Map<TaskGraphTransitionId, TaskGraphApplyRule>

    init {
        require(rules.map { it.transitionId }.toSet().size == rules.size) {
            "duplicate TaskGraph apply transition rule"
        }
        rulesByTransitionId = rules.associateBy { it.transitionId }
    }

    internal fun ruleFor(transitionId: TaskGraphTransitionId): TaskGraphApplyRule? =
        rulesByTransitionId[transitionId]
}

enum class TaskGraphApplyRejectReason {
    GRAPH_VERSION_MISMATCH,
    UNKNOWN_STATE,
    STALE_GENERATION,
    UNMAPPED_TRANSITION,
    TRANSITION_NOT_LEGAL_FROM_STATE,
    EVENT_MAPPING_MISMATCH,
    PROVENANCE_NOT_ALLOWED,
    AUTHORITY_BEARING_SLOT,
    SLOT_NOT_ALLOWED,
    SLOT_NOT_AUTHORIZED,
    MISSING_REQUIRED_SLOT,
    SLOT_SCHEMA_REJECTED,
    REDUCER_REJECTED,
}

sealed interface TaskGraphApplyResult {
    val snapshot: TaskGraphSnapshot

    data class Accepted(
        override val snapshot: TaskGraphSnapshot,
        val effects: List<TaskGraphEffect>,
        val record: TaskGraphEventRecord,
    ) : TaskGraphApplyResult

    data class Rejected(
        override val snapshot: TaskGraphSnapshot,
        val reason: TaskGraphApplyRejectReason,
        val reducerReason: TaskGraphRejectReason? = null,
    ) : TaskGraphApplyResult
}

/**
 * Explicit application-owned boundary from already validated candidate data to the pure reducer.
 *
 * The bridge performs freshness, state/mapping, provenance, slot scope, authorization and schema
 * checks before constructing a typed event. A rejected candidate never reaches TaskGraphCore.reduce.
 * Accepted reducer effects are returned as inert data for existing application owners to consume.
 */
class TaskGraphApplyBridge(
    private val definition: TaskGraphDefinition,
    private val core: TaskGraphCore,
    private val policy: TaskGraphApplyPolicy,
) {
    fun apply(
        snapshot: TaskGraphSnapshot,
        candidate: TaskGraphApplyCandidate,
        authorizedSlotIds: Set<TaskGraphSlotId>,
    ): TaskGraphApplyResult {
        if (snapshot.graphVersion != definition.version) {
            return rejected(snapshot, TaskGraphApplyRejectReason.GRAPH_VERSION_MISMATCH)
        }
        if (runCatching { definition.state(snapshot.state) }.isFailure) {
            return rejected(snapshot, TaskGraphApplyRejectReason.UNKNOWN_STATE)
        }
        if (candidate.generation != snapshot.generation) {
            return rejected(snapshot, TaskGraphApplyRejectReason.STALE_GENERATION)
        }

        val rule = policy.ruleFor(candidate.transitionId)
            ?: return rejected(snapshot, TaskGraphApplyRejectReason.UNMAPPED_TRANSITION)
        val transition = definition.transitions.singleOrNull { it.id == candidate.transitionId }
            ?: return rejected(snapshot, TaskGraphApplyRejectReason.UNMAPPED_TRANSITION)
        if (transition.from != snapshot.state) {
            return rejected(snapshot, TaskGraphApplyRejectReason.TRANSITION_NOT_LEGAL_FROM_STATE)
        }
        if (transition.event != rule.eventId) {
            return rejected(snapshot, TaskGraphApplyRejectReason.EVENT_MAPPING_MISMATCH)
        }
        if (candidate.provenance !in rule.allowedProvenance) {
            return rejected(snapshot, TaskGraphApplyRejectReason.PROVENANCE_NOT_ALLOWED)
        }

        val candidateSlotIds = candidate.slotCandidates.keys
        if (candidateSlotIds.any(::isAuthorityBearingSlot)) {
            return rejected(snapshot, TaskGraphApplyRejectReason.AUTHORITY_BEARING_SLOT)
        }
        if (candidateSlotIds.any { it !in rule.slotRules }) {
            return rejected(snapshot, TaskGraphApplyRejectReason.SLOT_NOT_ALLOWED)
        }
        if (candidateSlotIds.any { it !in authorizedSlotIds }) {
            return rejected(snapshot, TaskGraphApplyRejectReason.SLOT_NOT_AUTHORIZED)
        }
        if (rule.slotRules.any { (id, slotRule) -> slotRule.required && id !in candidateSlotIds }) {
            return rejected(snapshot, TaskGraphApplyRejectReason.MISSING_REQUIRED_SLOT)
        }
        if (
            candidate.slotCandidates.any { (id, value) ->
                !checkNotNull(rule.slotRules[id]).schema.accepts(value)
            }
        ) {
            return rejected(snapshot, TaskGraphApplyRejectReason.SLOT_SCHEMA_REJECTED)
        }

        val event = TaskGraphEvent(
            id = rule.eventId,
            generation = candidate.generation,
            candidates = candidate.slotCandidates,
        )
        return when (val reduction = core.reduce(snapshot, event)) {
            is TaskGraphReduction.Accepted -> TaskGraphApplyResult.Accepted(
                snapshot = reduction.snapshot,
                effects = reduction.effects.toList(),
                record = reduction.record,
            )
            is TaskGraphReduction.Rejected -> TaskGraphApplyResult.Rejected(
                snapshot = reduction.snapshot,
                reason = TaskGraphApplyRejectReason.REDUCER_REJECTED,
                reducerReason = reduction.reason,
            )
        }
    }

    private fun rejected(
        snapshot: TaskGraphSnapshot,
        reason: TaskGraphApplyRejectReason,
    ): TaskGraphApplyResult.Rejected = TaskGraphApplyResult.Rejected(
        snapshot = snapshot,
        reason = reason,
    )

    private fun isAuthorityBearingSlot(id: TaskGraphSlotId): Boolean =
        id.value.trim().uppercase() in AUTHORITY_BEARING_SLOT_IDS

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
    }
}
