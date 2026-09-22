package pl.michalmatu.aicallbridge.taskgraph

import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.DefaultState
import ru.nsk.kstatemachine.state.addInitialState
import ru.nsk.kstatemachine.state.addState
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.onTriggered

/**
 * Test-only spike. This intentionally adapts KStateMachine to the same immutable TaskGraph
 * contracts so dependency/lifecycle/authority cost can be compared with the custom reducer.
 */
class KStateMachineTaskGraphSpikeContractTest : TaskGraphContractTest() {
    override fun createCore(definition: TaskGraphDefinition): TaskGraphCore =
        KStateMachineTaskGraphSpikeCore(definition)
}

private class KStateMachineTaskGraphSpikeCore(
    private val definition: TaskGraphDefinition,
) : TaskGraphCore {
    private class SpikeEvent(val taskGraphEvent: TaskGraphEvent) : Event

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

        val stateIds = buildList {
            add(definition.initialState)
            definition.transitions.forEach { transition ->
                add(transition.from)
                add(transition.to)
            }
        }.distinct()
        val machineStates = stateIds.associateWith { id -> DefaultState(id.value) }
        var triggered: TaskGraphTransition? = null

        definition.transitions.forEach { transition ->
            val source = machineStates.getValue(transition.from)
            source.transition<SpikeEvent>(transition.id.value) {
                guard = {
                    event.taskGraphEvent.id == transition.event &&
                        transition.guard(snapshot, event.taskGraphEvent)
                }
                targetState = machineStates.getValue(transition.to)
                onTriggered { triggered = transition }
            }
        }

        val machine = createStdLibStateMachine {
            machineStates.forEach { (id, state) ->
                if (id == snapshot.state) {
                    addInitialState(state)
                } else {
                    addState(state)
                }
            }
        }
        machine.processEventBlocking(SpikeEvent(event))

        val transition = triggered
            ?: return rejected(snapshot, TaskGraphRejectReason.GUARD_REJECTED)

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
