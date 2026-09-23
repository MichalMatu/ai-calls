package pl.michalmatu.aicallbridge.appointment

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.taskgraph.CustomTaskGraphCore
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEvent
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphReduction
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue

class BookAppointmentTaskGraphPolicyNeutralProposalTest {
    private val scheduledAt = ZonedDateTime.of(
        2026,
        9,
        24,
        17,
        30,
        0,
        0,
        ZoneId.of("Europe/Warsaw"),
    )

    @Test
    fun `proposal transition is policy neutral and emits no owner effect`() {
        val core = CustomTaskGraphCore(BookAppointmentTaskGraph.definition)
        val initial = BookAppointmentTaskGraph.definition.initialSnapshot()

        val reduction = core.reduce(
            initial,
            TaskGraphEvent(
                id = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT,
                generation = initial.generation,
                candidates = mapOf(
                    BookAppointmentTaskGraph.APPOINTMENT_AT to
                        TaskGraphSlotValue.Text(scheduledAt.toString()),
                ),
            ),
        ) as TaskGraphReduction.Accepted

        assertEquals(BookAppointmentTaskGraph.PROPOSAL, reduction.snapshot.state)
        assertEquals(
            TaskGraphSlotValue.Text(scheduledAt.toString()),
            reduction.snapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT],
        )
        assertTrue(reduction.effects.isEmpty())
    }

    @Test
    fun `user rejection clears proposal candidate before returning to waiting offer`() {
        val core = CustomTaskGraphCore(BookAppointmentTaskGraph.definition)
        val initial = BookAppointmentTaskGraph.definition.initialSnapshot()
        val proposal = accepted(
            core,
            initial,
            TaskGraphEvent(
                id = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT,
                generation = initial.generation,
                candidates = mapOf(
                    BookAppointmentTaskGraph.APPOINTMENT_AT to
                        TaskGraphSlotValue.Text(scheduledAt.toString()),
                ),
            ),
        )
        val confirmation = accepted(
            core,
            proposal,
            TaskGraphEvent(
                id = BookAppointmentTaskGraph.REQUIRE_CONFIRMATION,
                generation = proposal.generation,
            ),
        )
        val waiting = accepted(
            core,
            confirmation,
            TaskGraphEvent(
                id = BookAppointmentTaskGraph.USER_REJECTED,
                generation = confirmation.generation,
            ),
        )

        assertEquals(BookAppointmentTaskGraph.WAITING_OFFER, waiting.state)
        assertFalse(waiting.context.contains(BookAppointmentTaskGraph.APPOINTMENT_AT))
    }

    private fun accepted(
        core: CustomTaskGraphCore,
        snapshot: pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot,
        event: TaskGraphEvent,
    ): pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot {
        assertEquals(snapshot.generation, event.generation)
        val reduction = core.reduce(snapshot, event)
        assertTrue(reduction is TaskGraphReduction.Accepted)
        return (reduction as TaskGraphReduction.Accepted).snapshot
    }
}
