package pl.michalmatu.aicallbridge.taskgraph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.dialogue.ValidatedSupervisorCandidate

class TaskGraphApplyBridgeTest {
    @Test
    fun `stale candidate fails before reducer`() {
        val fixture = fixture()
        val candidate = supervisorCandidate(
            generation = fixture.snapshot.generation + 1L,
            slots = mapOf(APPOINTMENT_AT to TaskGraphSlotValue.Text("2026-09-24T10:00:00+02:00[Europe/Warsaw]")),
        )

        val result = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(candidate),
            authorizedSlotIds = setOf(APPOINTMENT_AT),
        )

        assertEquals(TaskGraphApplyRejectReason.STALE_GENERATION, (result as TaskGraphApplyResult.Rejected).reason)
        assertEquals(0, fixture.core.reduceCalls)
        assertEquals(fixture.snapshot, result.snapshot)
    }

    @Test
    fun `unmapped or state illegal transition cannot become an event`() {
        val fixture = fixture()
        val unmapped = ValidatedSupervisorCandidate(
            generation = fixture.snapshot.generation,
            transitionId = TaskGraphTransitionId("missing-transition"),
            slotCandidates = emptyMap(),
            confidence = 0.99,
        )

        val unmappedResult = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(unmapped),
            authorizedSlotIds = emptySet(),
        )

        assertEquals(
            TaskGraphApplyRejectReason.UNMAPPED_TRANSITION,
            (unmappedResult as TaskGraphApplyResult.Rejected).reason,
        )
        assertEquals(0, fixture.core.reduceCalls)

        val moved = fixture.snapshot.copy(state = NEXT)
        val stateIllegalResult = fixture.bridge.apply(
            snapshot = moved,
            candidate = TaskGraphApplyCandidate.fromSupervisor(supervisorCandidate(moved.generation)),
            authorizedSlotIds = setOf(APPOINTMENT_AT),
        )

        assertEquals(
            TaskGraphApplyRejectReason.TRANSITION_NOT_LEGAL_FROM_STATE,
            (stateIllegalResult as TaskGraphApplyResult.Rejected).reason,
        )
        assertEquals(0, fixture.core.reduceCalls)
    }

    @Test
    fun `slot schema authorization and authority checks happen before commit`() {
        val fixture = fixture(includeAuthorityRule = true)
        val invalidValue = supervisorCandidate(
            generation = fixture.snapshot.generation,
            slots = mapOf(APPOINTMENT_AT to TaskGraphSlotValue.Text("tomorrow-ish")),
        )

        val invalidResult = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(invalidValue),
            authorizedSlotIds = setOf(APPOINTMENT_AT),
        )
        assertEquals(
            TaskGraphApplyRejectReason.SLOT_SCHEMA_REJECTED,
            (invalidResult as TaskGraphApplyResult.Rejected).reason,
        )
        assertFalse(invalidResult.snapshot.context.contains(APPOINTMENT_AT))
        assertEquals(0, fixture.core.reduceCalls)

        val unauthorizedResult = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(supervisorCandidate(fixture.snapshot.generation)),
            authorizedSlotIds = emptySet(),
        )
        assertEquals(
            TaskGraphApplyRejectReason.SLOT_NOT_AUTHORIZED,
            (unauthorizedResult as TaskGraphApplyResult.Rejected).reason,
        )
        assertEquals(0, fixture.core.reduceCalls)

        val authorityCandidate = supervisorCandidate(
            generation = fixture.snapshot.generation,
            slots = mapOf(DIAL to TaskGraphSlotValue.Text("+48123456789")),
        )
        val authorityResult = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(authorityCandidate),
            authorizedSlotIds = setOf(DIAL),
        )
        assertEquals(
            TaskGraphApplyRejectReason.AUTHORITY_BEARING_SLOT,
            (authorityResult as TaskGraphApplyResult.Rejected).reason,
        )
        assertEquals(0, fixture.core.reduceCalls)
    }

    @Test
    fun `provenance must be allowed before reducer`() {
        val fixture = fixture(allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC))

        val result = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(supervisorCandidate(fixture.snapshot.generation)),
            authorizedSlotIds = setOf(APPOINTMENT_AT),
        )

        assertEquals(
            TaskGraphApplyRejectReason.PROVENANCE_NOT_ALLOWED,
            (result as TaskGraphApplyResult.Rejected).reason,
        )
        assertEquals(0, fixture.core.reduceCalls)
    }

    @Test
    fun `accepted candidate reduces once and returns effects as data only`() {
        val fixture = fixture()
        val value = TaskGraphSlotValue.Text("2026-09-24T10:00:00+02:00[Europe/Warsaw]")

        val result = fixture.bridge.apply(
            snapshot = fixture.snapshot,
            candidate = TaskGraphApplyCandidate.fromSupervisor(
                supervisorCandidate(
                    generation = fixture.snapshot.generation,
                    slots = mapOf(APPOINTMENT_AT to value),
                ),
            ),
            authorizedSlotIds = setOf(APPOINTMENT_AT),
        )

        assertTrue(result is TaskGraphApplyResult.Accepted)
        result as TaskGraphApplyResult.Accepted
        assertEquals(1, fixture.core.reduceCalls)
        assertEquals(NEXT, result.snapshot.state)
        assertEquals(value, result.snapshot.context[APPOINTMENT_AT])
        assertEquals(listOf(TaskGraphEffect(EFFECT_ID)), result.effects)
        assertEquals(OFFER_EVENT, result.record.event.id)
        assertEquals(OFFER_TRANSITION, result.record.transitionId)
    }

    private fun fixture(
        allowedProvenance: Set<TaskGraphCandidateProvenance> = setOf(TaskGraphCandidateProvenance.SUPERVISOR),
        includeAuthorityRule: Boolean = false,
    ): Fixture {
        val definition = TaskGraphDefinition(
            version = 1,
            initialState = START,
            states = listOf(TaskGraphStateDefinition(START), TaskGraphStateDefinition(NEXT)),
            transitions = listOf(
                TaskGraphTransition(
                    id = OFFER_TRANSITION,
                    from = START,
                    event = OFFER_EVENT,
                    to = NEXT,
                    contextReducer = { context, event ->
                        context.with(APPOINTMENT_AT, requireNotNull(event.candidates[APPOINTMENT_AT]))
                    },
                    effects = listOf(TaskGraphEffect(EFFECT_ID)),
                ),
            ),
            maxRecoveryCount = 1,
        )
        val core = CountingCore(CustomTaskGraphCore(definition))
        val slotRules = buildMap {
            put(
                APPOINTMENT_AT,
                TaskGraphApplySlotRule(
                    required = true,
                    schema = TaskGraphApplySlotSchema.Text { value -> value.startsWith("2026-") },
                ),
            )
            if (includeAuthorityRule) {
                put(
                    DIAL,
                    TaskGraphApplySlotRule(
                        required = false,
                        schema = TaskGraphApplySlotSchema.Text(),
                    ),
                )
            }
        }
        val policy = TaskGraphApplyPolicy(
            rules = listOf(
                TaskGraphApplyRule(
                    transitionId = OFFER_TRANSITION,
                    eventId = OFFER_EVENT,
                    allowedProvenance = allowedProvenance,
                    slotRules = slotRules,
                ),
            ),
        )
        return Fixture(
            snapshot = definition.initialSnapshot(),
            core = core,
            bridge = TaskGraphApplyBridge(definition, core, policy),
        )
    }

    private fun supervisorCandidate(
        generation: Long,
        slots: Map<TaskGraphSlotId, TaskGraphSlotValue> = mapOf(
            APPOINTMENT_AT to TaskGraphSlotValue.Text("2026-09-24T10:00:00+02:00[Europe/Warsaw]"),
        ),
    ): ValidatedSupervisorCandidate = ValidatedSupervisorCandidate(
        generation = generation,
        transitionId = OFFER_TRANSITION,
        slotCandidates = slots,
        confidence = 0.99,
    )

    private data class Fixture(
        val snapshot: TaskGraphSnapshot,
        val core: CountingCore,
        val bridge: TaskGraphApplyBridge,
    )

    private class CountingCore(
        private val delegate: TaskGraphCore,
    ) : TaskGraphCore {
        var reduceCalls: Int = 0
            private set

        override fun reduce(snapshot: TaskGraphSnapshot, event: TaskGraphEvent): TaskGraphReduction {
            reduceCalls += 1
            return delegate.reduce(snapshot, event)
        }

        override fun replay(
            initialSnapshot: TaskGraphSnapshot,
            records: List<TaskGraphEventRecord>,
        ): TaskGraphReplayResult = delegate.replay(initialSnapshot, records)
    }

    private companion object {
        val START = TaskGraphStateId("START")
        val NEXT = TaskGraphStateId("NEXT")
        val OFFER_EVENT = TaskGraphEventId("OFFER_EVENT")
        val OFFER_TRANSITION = TaskGraphTransitionId("offer-transition")
        val APPOINTMENT_AT = TaskGraphSlotId("APPOINTMENT_AT")
        val DIAL = TaskGraphSlotId("DIAL")
        val EFFECT_ID = TaskGraphEffectId("PROPOSAL_READY")
    }
}
