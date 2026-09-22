package pl.michalmatu.aicallbridge.taskgraph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared TaskGraph v1 semantics. Implementations must satisfy this suite without owning side effects
 * or any telephony/commitment authority.
 */
abstract class TaskGraphContractTest {
    protected abstract fun createCore(definition: TaskGraphDefinition): TaskGraphCore

    @Test
    fun `state event transition and slot ids are distinct typed values`() {
        assertNotEquals(TaskGraphStateId("START").toString(), TaskGraphEventId("START").toString())
        assertNotEquals(TaskGraphEventId("START").toString(), TaskGraphTransitionId("START").toString())
        assertNotEquals(TaskGraphTransitionId("START").toString(), TaskGraphSlotId("START").toString())
    }

    @Test
    fun `only state compatible transition is accepted`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        val initial = definition.initialSnapshot()

        val accepted = core.reduce(initial, event(REQUESTED, initial))
        assertTrue(accepted is TaskGraphReduction.Accepted)
        val requested = (accepted as TaskGraphReduction.Accepted).snapshot
        assertEquals(REQUESTING, requested.state)

        val wrongState = core.reduce(requested, event(REQUESTED, requested))
        assertEquals(TaskGraphRejectReason.NO_COMPATIBLE_TRANSITION, (wrongState as TaskGraphReduction.Rejected).reason)
        assertEquals(requested, wrongState.snapshot)
    }

    @Test
    fun `pure guard rejects candidate without committing it`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        val requested = accept(core, definition.initialSnapshot(), REQUESTED)

        val rejected = core.reduce(
            requested,
            event(
                id = OFFER_RECEIVED,
                snapshot = requested,
                candidates = mapOf(DATE to TaskGraphSlotValue.Text("outside-window")),
            ),
        )

        assertEquals(TaskGraphRejectReason.GUARD_REJECTED, (rejected as TaskGraphReduction.Rejected).reason)
        assertFalse(rejected.snapshot.context.contains(DATE))
    }

    @Test
    fun `candidate is committed only by accepted transition context reducer`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        val requested = accept(core, definition.initialSnapshot(), REQUESTED)

        val proposal = accept(
            core = core,
            snapshot = requested,
            id = OFFER_RECEIVED,
            candidates = mapOf(DATE to TaskGraphSlotValue.Text("2026-09-24")),
        )

        assertEquals(PROPOSAL, proposal.state)
        assertEquals(TaskGraphSlotValue.Text("2026-09-24"), proposal.context[DATE])
    }

    @Test
    fun `recovery counter is bounded and accepted non recovery transition resets it`() {
        val definition = appointmentDefinition(maxRecoveryCount = 2)
        val core = createCore(definition)
        var snapshot = definition.initialSnapshot()

        snapshot = accept(core, snapshot, UNKNOWN)
        assertEquals(1, snapshot.recoveryCount)
        snapshot = accept(core, snapshot, UNKNOWN)
        assertEquals(2, snapshot.recoveryCount)

        val overflow = core.reduce(snapshot, event(UNKNOWN, snapshot)) as TaskGraphReduction.Rejected
        assertEquals(TaskGraphRejectReason.RECOVERY_LIMIT_EXCEEDED, overflow.reason)
        assertEquals(2, overflow.snapshot.recoveryCount)

        snapshot = accept(core, snapshot, REQUESTED)
        assertEquals(0, snapshot.recoveryCount)
    }

    @Test
    fun `proposal confirmation commitment and completion remain explicit states`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        var snapshot = definition.initialSnapshot()

        snapshot = accept(core, snapshot, REQUESTED)
        snapshot = accept(
            core,
            snapshot,
            OFFER_RECEIVED,
            mapOf(DATE to TaskGraphSlotValue.Text("2026-09-24")),
        )
        assertEquals(PROPOSAL, snapshot.state)
        snapshot = accept(core, snapshot, CONFIRMATION_REQUIRED)
        assertEquals(CONFIRMATION, snapshot.state)
        snapshot = accept(core, snapshot, USER_CONFIRMED)
        assertEquals(COMMITMENT, snapshot.state)
        snapshot = accept(core, snapshot, COMMITTED)
        assertEquals(COMPLETE, snapshot.state)
        assertEquals(TaskGraphStateKind.COMPLETED, definition.state(COMPLETE).kind)
    }

    @Test
    fun `failure and takeover are explicit terminal states`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)

        val failed = accept(core, definition.initialSnapshot(), FAIL)
        assertEquals(FAILED, failed.state)
        assertEquals(TaskGraphStateKind.FAILED, definition.state(FAILED).kind)
        assertEquals(TaskGraphRejectReason.TERMINAL_STATE, (core.reduce(failed, event(REQUESTED, failed)) as TaskGraphReduction.Rejected).reason)

        val takeover = accept(core, definition.initialSnapshot(), TAKE_OVER_EVENT)
        assertEquals(TAKE_OVER, takeover.state)
        assertEquals(TaskGraphStateKind.TAKE_OVER, definition.state(TAKE_OVER).kind)
    }

    @Test
    fun `effects are returned as data and never executed by the core`() {
        var sideEffectExecutions = 0
        val definition = appointmentDefinition(
            proposalEffects = listOf(TaskGraphEffect(TaskGraphEffectId("REQUEST_USER_CONFIRMATION"))),
        )
        val core = createCore(definition)
        var snapshot = definition.initialSnapshot()
        snapshot = accept(core, snapshot, REQUESTED)

        val result = core.reduce(
            snapshot,
            event(
                OFFER_RECEIVED,
                snapshot,
                mapOf(DATE to TaskGraphSlotValue.Text("2026-09-24")),
            ),
        ) as TaskGraphReduction.Accepted

        assertEquals(listOf(TaskGraphEffect(TaskGraphEffectId("REQUEST_USER_CONFIRMATION"))), result.effects)
        assertEquals(0, sideEffectExecutions)
        sideEffectExecutions += result.effects.size
        assertEquals(1, sideEffectExecutions)
    }

    @Test
    fun `accepted transitions emit versioned replayable event records`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        var snapshot = definition.initialSnapshot()
        val records = mutableListOf<TaskGraphEventRecord>()

        val first = core.reduce(snapshot, event(REQUESTED, snapshot)) as TaskGraphReduction.Accepted
        records += first.record
        snapshot = first.snapshot
        val second = core.reduce(
            snapshot,
            event(OFFER_RECEIVED, snapshot, mapOf(DATE to TaskGraphSlotValue.Text("2026-09-24"))),
        ) as TaskGraphReduction.Accepted
        records += second.record
        snapshot = second.snapshot

        assertEquals(TaskGraphEventRecord.SCHEMA_VERSION, records.first().schemaVersion)
        assertEquals(definition.version, records.first().graphVersion)
        assertEquals(1L, records.first().sequence)
        assertEquals(0L, records.first().generationBefore)
        assertEquals(1L, records.first().generationAfter)
        assertEquals(2L, records.last().sequence)

        val replay = core.replay(definition.initialSnapshot(), records)
        assertTrue(replay is TaskGraphReplayResult.Success)
        assertEquals(snapshot, (replay as TaskGraphReplayResult.Success).snapshot)
    }

    @Test
    fun `stale event generation is rejected without mutation`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        val snapshot = accept(core, definition.initialSnapshot(), REQUESTED)

        val stale = core.reduce(snapshot, TaskGraphEvent(UNKNOWN, generation = 0L)) as TaskGraphReduction.Rejected

        assertEquals(TaskGraphRejectReason.STALE_GENERATION, stale.reason)
        assertEquals(snapshot, stale.snapshot)
    }

    @Test
    fun `replay rejects tampered schema version transition and state evidence`() {
        val definition = appointmentDefinition()
        val core = createCore(definition)
        val initial = definition.initialSnapshot()
        val accepted = core.reduce(initial, event(REQUESTED, initial)) as TaskGraphReduction.Accepted

        val badSchema = core.replay(initial, listOf(accepted.record.copy(schemaVersion = 99)))
        assertEquals(TaskGraphReplayRejectReason.UNSUPPORTED_SCHEMA, (badSchema as TaskGraphReplayResult.Rejected).reason)

        val badTransition = core.replay(
            initial,
            listOf(accepted.record.copy(transitionId = TaskGraphTransitionId("TAMPERED"))),
        )
        assertEquals(TaskGraphReplayRejectReason.RECORD_MISMATCH, (badTransition as TaskGraphReplayResult.Rejected).reason)

        val badState = core.replay(
            initial,
            listOf(accepted.record.copy(stateBefore = REQUESTING)),
        )
        assertEquals(TaskGraphReplayRejectReason.RECORD_MISMATCH, (badState as TaskGraphReplayResult.Rejected).reason)
    }

    private fun accept(
        core: TaskGraphCore,
        snapshot: TaskGraphSnapshot,
        id: TaskGraphEventId,
        candidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
    ): TaskGraphSnapshot {
        val result = core.reduce(snapshot, event(id, snapshot, candidates))
        assertTrue("expected accepted reduction but was $result", result is TaskGraphReduction.Accepted)
        return (result as TaskGraphReduction.Accepted).snapshot
    }

    private fun event(
        id: TaskGraphEventId,
        snapshot: TaskGraphSnapshot,
        candidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
    ) = TaskGraphEvent(id = id, generation = snapshot.generation, candidates = candidates)

    protected fun appointmentDefinition(
        maxRecoveryCount: Int = 2,
        proposalEffects: List<TaskGraphEffect> = emptyList(),
    ): TaskGraphDefinition = TaskGraphDefinition(
        version = 1,
        initialState = START,
        states = listOf(
            TaskGraphStateDefinition(START),
            TaskGraphStateDefinition(REQUESTING),
            TaskGraphStateDefinition(PROPOSAL, TaskGraphStateKind.PROPOSAL),
            TaskGraphStateDefinition(CONFIRMATION, TaskGraphStateKind.CONFIRMATION),
            TaskGraphStateDefinition(COMMITMENT, TaskGraphStateKind.COMMITMENT),
            TaskGraphStateDefinition(COMPLETE, TaskGraphStateKind.COMPLETED),
            TaskGraphStateDefinition(FAILED, TaskGraphStateKind.FAILED),
            TaskGraphStateDefinition(TAKE_OVER, TaskGraphStateKind.TAKE_OVER),
        ),
        transitions = listOf(
            TaskGraphTransition(
                id = TaskGraphTransitionId("request"),
                from = START,
                event = REQUESTED,
                to = REQUESTING,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("unknown"),
                from = START,
                event = UNKNOWN,
                to = START,
                recovery = TaskGraphRecoveryMutation.INCREMENT,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("offer"),
                from = REQUESTING,
                event = OFFER_RECEIVED,
                to = PROPOSAL,
                guard = { _, candidate -> candidate.candidates[DATE] == TaskGraphSlotValue.Text("2026-09-24") },
                contextReducer = { context, candidate ->
                    context.with(DATE, checkNotNull(candidate.candidates[DATE]))
                },
                effects = proposalEffects,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("confirm-required"),
                from = PROPOSAL,
                event = CONFIRMATION_REQUIRED,
                to = CONFIRMATION,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("confirmed"),
                from = CONFIRMATION,
                event = USER_CONFIRMED,
                to = COMMITMENT,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("committed"),
                from = COMMITMENT,
                event = COMMITTED,
                to = COMPLETE,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("fail"),
                from = START,
                event = FAIL,
                to = FAILED,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("take-over"),
                from = START,
                event = TAKE_OVER_EVENT,
                to = TAKE_OVER,
            ),
        ),
        maxRecoveryCount = maxRecoveryCount,
    )

    protected companion object {
        val START = TaskGraphStateId("START")
        val REQUESTING = TaskGraphStateId("REQUESTING")
        val PROPOSAL = TaskGraphStateId("PROPOSAL")
        val CONFIRMATION = TaskGraphStateId("CONFIRMATION")
        val COMMITMENT = TaskGraphStateId("COMMITMENT")
        val COMPLETE = TaskGraphStateId("COMPLETE")
        val FAILED = TaskGraphStateId("FAILED")
        val TAKE_OVER = TaskGraphStateId("TAKE_OVER")

        val REQUESTED = TaskGraphEventId("REQUESTED")
        val OFFER_RECEIVED = TaskGraphEventId("OFFER_RECEIVED")
        val CONFIRMATION_REQUIRED = TaskGraphEventId("CONFIRMATION_REQUIRED")
        val USER_CONFIRMED = TaskGraphEventId("USER_CONFIRMED")
        val COMMITTED = TaskGraphEventId("COMMITTED")
        val UNKNOWN = TaskGraphEventId("UNKNOWN")
        val FAIL = TaskGraphEventId("FAIL")
        val TAKE_OVER_EVENT = TaskGraphEventId("TAKE_OVER")

        val DATE = TaskGraphSlotId("date")
    }
}

class CustomTaskGraphContractTest : TaskGraphContractTest() {
    override fun createCore(definition: TaskGraphDefinition): TaskGraphCore = CustomTaskGraphCore(definition)
}
