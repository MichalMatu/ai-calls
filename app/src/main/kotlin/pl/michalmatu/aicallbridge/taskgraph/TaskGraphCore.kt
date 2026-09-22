package pl.michalmatu.aicallbridge.taskgraph

data class TaskGraphStateId(val value: String) {
    init { require(value.isNotBlank()) }
}

data class TaskGraphEventId(val value: String) {
    init { require(value.isNotBlank()) }
}

data class TaskGraphTransitionId(val value: String) {
    init { require(value.isNotBlank()) }
}

data class TaskGraphSlotId(val value: String) {
    init { require(value.isNotBlank()) }
}

data class TaskGraphEffectId(val value: String) {
    init { require(value.isNotBlank()) }
}

sealed interface TaskGraphSlotValue {
    data class Text(val value: String) : TaskGraphSlotValue
}

data class TaskGraphContext(
    private val values: Map<TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
) {
    operator fun get(id: TaskGraphSlotId): TaskGraphSlotValue? = values[id]

    fun contains(id: TaskGraphSlotId): Boolean = values.containsKey(id)

    fun with(id: TaskGraphSlotId, value: TaskGraphSlotValue): TaskGraphContext =
        TaskGraphContext(values + (id to value))

    internal fun asMap(): Map<TaskGraphSlotId, TaskGraphSlotValue> = values.toMap()
}

enum class TaskGraphStateKind {
    NORMAL,
    PROPOSAL,
    CONFIRMATION,
    COMMITMENT,
    COMPLETED,
    FAILED,
    TAKE_OVER,
    ;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == TAKE_OVER
}

data class TaskGraphStateDefinition(
    val id: TaskGraphStateId,
    val kind: TaskGraphStateKind = TaskGraphStateKind.NORMAL,
)

enum class TaskGraphRecoveryMutation {
    RESET,
    INCREMENT,
    KEEP,
}

data class TaskGraphEffect(val id: TaskGraphEffectId)

data class TaskGraphEvent(
    val id: TaskGraphEventId,
    val generation: Long,
    val candidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
) {
    init { require(generation >= 0L) }
}

data class TaskGraphSnapshot(
    val graphVersion: Int,
    val state: TaskGraphStateId,
    val context: TaskGraphContext = TaskGraphContext(),
    val recoveryCount: Int = 0,
    val sequence: Long = 0L,
    val generation: Long = 0L,
) {
    init {
        require(graphVersion > 0)
        require(recoveryCount >= 0)
        require(sequence >= 0L)
        require(generation >= 0L)
    }
}

data class TaskGraphTransition(
    val id: TaskGraphTransitionId,
    val from: TaskGraphStateId,
    val event: TaskGraphEventId,
    val to: TaskGraphStateId,
    val guard: (TaskGraphSnapshot, TaskGraphEvent) -> Boolean = { _, _ -> true },
    val contextReducer: (TaskGraphContext, TaskGraphEvent) -> TaskGraphContext = { context, _ -> context },
    val recovery: TaskGraphRecoveryMutation = TaskGraphRecoveryMutation.RESET,
    val effects: List<TaskGraphEffect> = emptyList(),
)

class TaskGraphDefinition(
    val version: Int,
    val initialState: TaskGraphStateId,
    states: List<TaskGraphStateDefinition>,
    transitions: List<TaskGraphTransition>,
    val maxRecoveryCount: Int,
) {
    private val statesById: Map<TaskGraphStateId, TaskGraphStateDefinition>
    internal val transitions: List<TaskGraphTransition>

    init {
        require(version > 0)
        require(maxRecoveryCount >= 0)
        require(states.isNotEmpty())
        require(states.map { it.id }.toSet().size == states.size) { "duplicate state id" }
        require(transitions.map { it.id }.toSet().size == transitions.size) { "duplicate transition id" }

        statesById = states.associateBy { it.id }
        require(statesById.containsKey(initialState)) { "initial state is not declared" }
        transitions.forEach { transition ->
            require(statesById.containsKey(transition.from)) { "transition source state is not declared" }
            require(statesById.containsKey(transition.to)) { "transition target state is not declared" }
            require(!checkNotNull(statesById[transition.from]).kind.isTerminal) {
                "terminal states cannot have outgoing transitions"
            }
        }
        this.transitions = transitions.toList()
    }

    fun state(id: TaskGraphStateId): TaskGraphStateDefinition =
        requireNotNull(statesById[id]) { "unknown state id: $id" }

    fun initialSnapshot(): TaskGraphSnapshot = TaskGraphSnapshot(
        graphVersion = version,
        state = initialState,
    )
}

enum class TaskGraphRejectReason {
    GRAPH_VERSION_MISMATCH,
    UNKNOWN_STATE,
    STALE_GENERATION,
    TERMINAL_STATE,
    NO_COMPATIBLE_TRANSITION,
    AMBIGUOUS_TRANSITION,
    GUARD_REJECTED,
    RECOVERY_LIMIT_EXCEEDED,
}

data class TaskGraphEventRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val graphVersion: Int,
    val sequence: Long,
    val generationBefore: Long,
    val generationAfter: Long,
    val transitionId: TaskGraphTransitionId,
    val event: TaskGraphEvent,
    val stateBefore: TaskGraphStateId,
    val stateAfter: TaskGraphStateId,
    val recoveryCountAfter: Int,
    val contextAfter: TaskGraphContext,
    val effects: List<TaskGraphEffect>,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

sealed interface TaskGraphReduction {
    val snapshot: TaskGraphSnapshot

    data class Accepted(
        override val snapshot: TaskGraphSnapshot,
        val effects: List<TaskGraphEffect>,
        val record: TaskGraphEventRecord,
    ) : TaskGraphReduction

    data class Rejected(
        override val snapshot: TaskGraphSnapshot,
        val reason: TaskGraphRejectReason,
    ) : TaskGraphReduction
}

enum class TaskGraphReplayRejectReason {
    UNSUPPORTED_SCHEMA,
    GRAPH_VERSION_MISMATCH,
    SEQUENCE_MISMATCH,
    RECORD_MISMATCH,
    REDUCTION_REJECTED,
}

sealed interface TaskGraphReplayResult {
    val snapshot: TaskGraphSnapshot

    data class Success(override val snapshot: TaskGraphSnapshot) : TaskGraphReplayResult

    data class Rejected(
        override val snapshot: TaskGraphSnapshot,
        val reason: TaskGraphReplayRejectReason,
    ) : TaskGraphReplayResult
}

interface TaskGraphCore {
    fun reduce(snapshot: TaskGraphSnapshot, event: TaskGraphEvent): TaskGraphReduction

    fun replay(
        initialSnapshot: TaskGraphSnapshot,
        records: List<TaskGraphEventRecord>,
    ): TaskGraphReplayResult
}

/**
 * Pure application-owned reducer. It returns effects as data and never executes them.
 */
class CustomTaskGraphCore(
    private val definition: TaskGraphDefinition,
) : TaskGraphCore {
    override fun reduce(snapshot: TaskGraphSnapshot, event: TaskGraphEvent): TaskGraphReduction {
        if (snapshot.graphVersion != definition.version) {
            return rejected(snapshot, TaskGraphRejectReason.GRAPH_VERSION_MISMATCH)
        }

        val currentState = runCatching { definition.state(snapshot.state) }.getOrNull()
            ?: return rejected(snapshot, TaskGraphRejectReason.UNKNOWN_STATE)

        if (event.generation != snapshot.generation) {
            return rejected(snapshot, TaskGraphRejectReason.STALE_GENERATION)
        }
        if (currentState.kind.isTerminal) {
            return rejected(snapshot, TaskGraphRejectReason.TERMINAL_STATE)
        }

        val compatible = definition.transitions.filter { transition ->
            transition.from == snapshot.state && transition.event == event.id
        }
        if (compatible.isEmpty()) {
            return rejected(snapshot, TaskGraphRejectReason.NO_COMPATIBLE_TRANSITION)
        }
        if (compatible.size > 1) {
            return rejected(snapshot, TaskGraphRejectReason.AMBIGUOUS_TRANSITION)
        }

        val transition = compatible.single()
        if (!transition.guard(snapshot, event)) {
            return rejected(snapshot, TaskGraphRejectReason.GUARD_REJECTED)
        }

        val nextRecoveryCount = when (transition.recovery) {
            TaskGraphRecoveryMutation.RESET -> 0
            TaskGraphRecoveryMutation.INCREMENT -> snapshot.recoveryCount + 1
            TaskGraphRecoveryMutation.KEEP -> snapshot.recoveryCount
        }
        if (nextRecoveryCount > definition.maxRecoveryCount) {
            return rejected(snapshot, TaskGraphRejectReason.RECOVERY_LIMIT_EXCEEDED)
        }

        val nextContext = transition.contextReducer(snapshot.context, event)
        val nextSnapshot = snapshot.copy(
            state = transition.to,
            context = nextContext,
            recoveryCount = nextRecoveryCount,
            sequence = snapshot.sequence + 1L,
            generation = snapshot.generation + 1L,
        )
        val effects = transition.effects.toList()
        val record = TaskGraphEventRecord(
            graphVersion = definition.version,
            sequence = nextSnapshot.sequence,
            generationBefore = snapshot.generation,
            generationAfter = nextSnapshot.generation,
            transitionId = transition.id,
            event = event,
            stateBefore = snapshot.state,
            stateAfter = nextSnapshot.state,
            recoveryCountAfter = nextSnapshot.recoveryCount,
            contextAfter = nextSnapshot.context,
            effects = effects,
        )
        return TaskGraphReduction.Accepted(nextSnapshot, effects, record)
    }

    override fun replay(
        initialSnapshot: TaskGraphSnapshot,
        records: List<TaskGraphEventRecord>,
    ): TaskGraphReplayResult {
        var snapshot = initialSnapshot
        for (record in records) {
            if (record.schemaVersion != TaskGraphEventRecord.SCHEMA_VERSION) {
                return TaskGraphReplayResult.Rejected(snapshot, TaskGraphReplayRejectReason.UNSUPPORTED_SCHEMA)
            }
            if (record.graphVersion != definition.version || snapshot.graphVersion != definition.version) {
                return TaskGraphReplayResult.Rejected(snapshot, TaskGraphReplayRejectReason.GRAPH_VERSION_MISMATCH)
            }
            if (record.sequence != snapshot.sequence + 1L) {
                return TaskGraphReplayResult.Rejected(snapshot, TaskGraphReplayRejectReason.SEQUENCE_MISMATCH)
            }
            if (
                record.generationBefore != snapshot.generation ||
                record.generationAfter != snapshot.generation + 1L ||
                record.stateBefore != snapshot.state ||
                record.event.generation != snapshot.generation
            ) {
                return TaskGraphReplayResult.Rejected(snapshot, TaskGraphReplayRejectReason.RECORD_MISMATCH)
            }

            val reduction = reduce(snapshot, record.event)
            if (reduction !is TaskGraphReduction.Accepted) {
                return TaskGraphReplayResult.Rejected(snapshot, TaskGraphReplayRejectReason.REDUCTION_REJECTED)
            }
            if (reduction.record != record) {
                return TaskGraphReplayResult.Rejected(snapshot, TaskGraphReplayRejectReason.RECORD_MISMATCH)
            }
            snapshot = reduction.snapshot
        }
        return TaskGraphReplayResult.Success(snapshot)
    }

    private fun rejected(
        snapshot: TaskGraphSnapshot,
        reason: TaskGraphRejectReason,
    ): TaskGraphReduction.Rejected = TaskGraphReduction.Rejected(snapshot, reason)
}
