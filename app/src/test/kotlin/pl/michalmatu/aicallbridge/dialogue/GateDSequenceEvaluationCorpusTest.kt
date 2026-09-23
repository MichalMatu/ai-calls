package pl.michalmatu.aicallbridge.dialogue

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.appointment.BookAppointmentEvidenceType
import pl.michalmatu.aicallbridge.appointment.BookAppointmentOutcome
import pl.michalmatu.aicallbridge.appointment.BookAppointmentSimulationStep
import pl.michalmatu.aicallbridge.appointment.BookAppointmentSimulator
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.DefaultFactDisclosurePolicy
import pl.michalmatu.aicallbridge.identity.FactDisclosureDecision
import pl.michalmatu.aicallbridge.identity.FactDisclosureRequest
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.taskgraph.CustomTaskGraphCore
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEvent
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventRecord
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphReduction
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphRejectReason
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphReplayResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

/**
 * Host-only Gate D acceptance corpus for sequence behavior.
 *
 * These scenarios intentionally compose existing owners instead of introducing a product
 * orchestrator. They assert deterministic evidence/replay and fail-closed policy boundaries only.
 */
class GateDSequenceEvaluationCorpusTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val allowedWindow = CallTimeWindow(
        ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 9, 24, 18, 0, 0, 0, zone),
    )
    private val target = CallResolvedTarget("Example clinic", "+48123456789")

    @Test
    fun `repeated deterministic unknowns escalate categorically and graph recovery stops at limit`() {
        val fitPolicy = DefaultDialogueFitPolicy(maxRecoveryCount = 2)
        val levels = (0..2).map { recoveryCount ->
            fitPolicy.evaluate(
                signals(
                    deterministicUnderstanding = DeterministicUnderstanding.UNKNOWN,
                    recoveryCount = recoveryCount,
                ),
            ).level
        }
        assertEquals(
            listOf(DialogueFitLevel.UNCERTAIN, DialogueFitLevel.LOW, DialogueFitLevel.BROKEN),
            levels,
        )

        val core = CustomTaskGraphCore(BookAppointmentTaskGraph.definition)
        val initial = BookAppointmentTaskGraph.definition.initialSnapshot()
        var snapshot = initial
        val records = mutableListOf<TaskGraphEventRecord>()

        repeat(2) {
            val reduction = core.reduce(
                snapshot,
                TaskGraphEvent(BookAppointmentTaskGraph.HARMLESS_QUESTION, snapshot.generation),
            )
            assertTrue(reduction is TaskGraphReduction.Accepted)
            val accepted = reduction as TaskGraphReduction.Accepted
            snapshot = accepted.snapshot
            records += accepted.record
        }

        val exhausted = core.reduce(
            snapshot,
            TaskGraphEvent(BookAppointmentTaskGraph.HARMLESS_QUESTION, snapshot.generation),
        )
        assertTrue(exhausted is TaskGraphReduction.Rejected)
        assertEquals(
            TaskGraphRejectReason.RECOVERY_LIMIT_EXCEEDED,
            (exhausted as TaskGraphReduction.Rejected).reason,
        )

        val replay = core.replay(initial, records)
        assertTrue(replay is TaskGraphReplayResult.Success)
        assertEquals(snapshot, (replay as TaskGraphReplayResult.Success).snapshot)
    }

    @Test
    fun `ambiguous offer can recover to one replayable booking`() {
        val result = simulator(task()).run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer(
                    "Mamy termin 2026-09-24 po południu",
                ),
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:00"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertTrue(result.replayVerified)
        val clarification = result.evidence.indexOfFirst {
            it.type == BookAppointmentEvidenceType.CLARIFICATION_REQUIRED
        }
        val parsed = result.evidence.indexOfLast {
            it.type == BookAppointmentEvidenceType.OFFER_PARSED
        }
        val committed = result.evidence.indexOfFirst {
            it.type == BookAppointmentEvidenceType.COMMITMENT_CONSUMED
        }
        assertTrue(clarification >= 0 && parsed > clarification && committed > parsed)
    }

    @Test
    fun `hard rejected offer cannot enter graph context before an acceptable alternative`() {
        val result = simulator(task()).run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 20:00"),
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:00"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertTrue(result.replayVerified)
        assertTrue(result.evidence.any { it.type == BookAppointmentEvidenceType.OFFER_REJECTED })
        assertEquals(1, result.evidence.count { it.type == BookAppointmentEvidenceType.PROPOSAL_CREATED })
        assertFalse(result.eventRecords.any { record ->
            record.event.candidates.values.any { candidate ->
                candidate == TaskGraphSlotValue.Text("2026-09-24T20:00+02:00[Europe/Warsaw]")
            }
        })
    }

    @Test
    fun `user rejection resumes negotiation and still consumes exactly one commitment`() {
        val preferredWindow = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val result = simulator(
            task(CallPreferences(listOf(preferredWindow), emptyList(), emptyList())),
        ).run(
            listOf(
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 17:30"),
                BookAppointmentSimulationStep.UserRejectsProposal,
                BookAppointmentSimulationStep.ReceptionistOffer("2026-09-24 16:30"),
            ),
        )

        assertEquals(BookAppointmentOutcome.BOOKED, result.outcome)
        assertTrue(result.replayVerified)
        val rejected = result.evidence.indexOfFirst {
            it.type == BookAppointmentEvidenceType.USER_REJECTED_PROPOSAL
        }
        val committed = result.evidence.indexOfFirst {
            it.type == BookAppointmentEvidenceType.COMMITMENT_CONSUMED
        }
        assertTrue(rejected >= 0 && committed > rejected)
        assertEquals(
            1,
            result.evidence.count { it.type == BookAppointmentEvidenceType.COMMITMENT_CONSUMED },
        )
    }

    @Test
    fun `identity disclosure corpus distinguishes ask-user and deny without plaintext`() {
        val task = task()
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 11L,
            availableFields = setOf(IdentityFieldId.PESEL, IdentityFieldId.EMAIL),
            authorizedFields = setOf(IdentityFieldId.PESEL),
            highSensitivityApprovedFields = emptySet(),
            allowedDisclosureStates = mapOf(
                IdentityFieldId.PESEL to setOf(BookAppointmentTaskGraph.WAITING_OFFER),
            ),
        )
        val policy = DefaultFactDisclosurePolicy()

        fun decision(fieldId: IdentityFieldId, mode: CallTaskMode): FactDisclosureDecision =
            policy.decide(
                FactDisclosureRequest(
                    task = task,
                    target = target,
                    currentState = BookAppointmentTaskGraph.WAITING_OFFER,
                    fieldId = fieldId,
                    mode = mode,
                    snapshotGeneration = 11L,
                ),
                facts,
            )

        assertEquals(FactDisclosureDecision.ASK_USER, decision(IdentityFieldId.PESEL, CallTaskMode.GENUINE))
        assertEquals(FactDisclosureDecision.DENY, decision(IdentityFieldId.PESEL, CallTaskMode.TEST))
        assertEquals(FactDisclosureDecision.ASK_USER, decision(IdentityFieldId.EMAIL, CallTaskMode.GENUINE))
    }

    @Test
    fun `cancel and takeover remain replayable terminal business outcomes`() {
        val cancelled = simulator(task()).run(listOf(BookAppointmentSimulationStep.Cancel))
        assertEquals(BookAppointmentOutcome.CANCELLED, cancelled.outcome)
        assertEquals(BookAppointmentTaskGraph.CANCELLED, cancelled.finalSnapshot.state)
        assertTrue(cancelled.replayVerified)

        val takenOver = simulator(task()).run(listOf(BookAppointmentSimulationStep.TakeOver))
        assertEquals(BookAppointmentOutcome.TAKE_OVER, takenOver.outcome)
        assertEquals(BookAppointmentTaskGraph.TAKE_OVER, takenOver.finalSnapshot.state)
        assertTrue(takenOver.replayVerified)
    }

    @Test
    fun `stale supervisor result remains candidate-only and fails closed`() {
        val transitionId = TaskGraphTransitionId("offer-candidate")
        val observation = ShadowDialogueObservation(
            generation = 8L,
            taskGoal = "book appointment",
            state = BookAppointmentTaskGraph.WAITING_OFFER,
            allowedTransitions = setOf(transitionId),
            validatedSlots = emptyMap(),
            availableFacts = emptySet(),
            finalizedTranscript = "counterparty offered a time",
        )
        val hypothesis = ShadowDialogueHypothesis(
            generation = 7L,
            suggestedTransition = transitionId,
            slotCandidates = emptyMap(),
            confidence = 0.99,
        )

        val validation = SupervisorProposalValidator().validate(
            observation = observation,
            hypothesis = hypothesis,
            allowedNonSecretSlots = emptySet(),
        )

        assertTrue(validation is SupervisorProposalValidation.Rejected)
        assertEquals(
            SupervisorProposalRejectReason.STALE_GENERATION,
            (validation as SupervisorProposalValidation.Rejected).reason,
        )
    }

    @Test
    fun `clean recovery requires consecutive evidence before returning high`() {
        val policy = DefaultDialogueFitPolicy(maxRecoveryCount = 2)
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)

        val low = hysteresis.update(
            policy.evaluate(signals(contradictionDetected = true)),
        )
        val firstClean = hysteresis.update(policy.evaluate(signals()))
        val secondClean = hysteresis.update(policy.evaluate(signals()))

        assertEquals(DialogueFitLevel.LOW, low.effective.level)
        assertEquals(DialogueFitLevel.LOW, firstClean.effective.level)
        assertEquals(DialogueFitLevel.HIGH, secondClean.effective.level)
        assertTrue(DialogueFitReason.CONTRADICTION in firstClean.effective.reasons)
    }

    private fun signals(
        deterministicUnderstanding: DeterministicUnderstanding = DeterministicUnderstanding.MATCHED,
        contradictionDetected: Boolean = false,
        recoveryCount: Int = 0,
    ) = DialogueFitSignals(
        sttQuality = DialogueSttQuality.GOOD,
        deterministicUnderstanding = deterministicUnderstanding,
        parserCompleteness = ParserCompleteness.COMPLETE,
        stateCompatibility = StateCompatibility.COMPATIBLE,
        contradictionDetected = contradictionDetected,
        missingRequiredSlots = 0,
        recoveryCount = recoveryCount,
        shadowComparison = ShadowComparison.NOT_AVAILABLE,
    )

    private fun task(preferences: CallPreferences = CallPreferences.none()): CallTask = CallTask(
        "Example clinic",
        "book appointment",
        "appointment",
        CallConstraints(listOf(allowedWindow), null, emptySet()),
        preferences,
        emptyMap(),
    )

    private fun simulator(task: CallTask): BookAppointmentSimulator {
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 11L,
            availableFields = emptySet(),
            authorizedFields = emptySet(),
        )
        return BookAppointmentSimulator(task, target, facts, zone)
    }
}
