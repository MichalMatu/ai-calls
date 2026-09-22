package pl.michalmatu.aicallbridge.dialogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

class SupervisorProposalValidatorContractTest {
    private val allowedTransition = TaskGraphTransitionId("offer-received")
    private val allowedSlot = TaskGraphSlotId("APPOINTMENT_TIME")
    private val observation = ShadowDialogueObservation(
        generation = 11L,
        taskGoal = "book appointment",
        state = TaskGraphStateId("WAITING_OFFER"),
        allowedTransitions = setOf(allowedTransition),
        validatedSlots = emptyMap(),
        availableFacts = emptySet(),
        finalizedTranscript = "Mamy wolny termin o 17:30",
    )
    private val validator = SupervisorProposalValidator(minimumConfidence = 0.80)

    @Test
    fun `fresh bounded proposal is accepted only as validated candidate data`() {
        val result = validator.validate(
            observation = observation,
            hypothesis = hypothesis(
                transition = allowedTransition,
                slots = mapOf(allowedSlot to TaskGraphSlotValue.Text("17:30")),
                confidence = 0.91,
            ),
            allowedNonSecretSlots = setOf(allowedSlot),
        )

        assertTrue(result is SupervisorProposalValidation.Accepted)
        val accepted = result as SupervisorProposalValidation.Accepted
        assertEquals(allowedTransition, accepted.candidate.transitionId)
        assertEquals(11L, accepted.candidate.generation)
        assertEquals(TaskGraphSlotValue.Text("17:30"), accepted.candidate.slotCandidates[allowedSlot])
        assertEquals(0.91, accepted.candidate.confidence, 0.0)
    }

    @Test
    fun `stale supervisor generation is rejected`() {
        val result = validator.validate(
            observation,
            hypothesis(transition = allowedTransition, generation = 10L),
            setOf(allowedSlot),
        )

        assertRejected(result, SupervisorProposalRejectReason.STALE_GENERATION)
    }

    @Test
    fun `missing or currently disallowed transition is rejected`() {
        val missing = validator.validate(
            observation,
            hypothesis(transition = null),
            setOf(allowedSlot),
        )
        val unknown = validator.validate(
            observation,
            hypothesis(transition = TaskGraphTransitionId("commit-now")),
            setOf(allowedSlot),
        )

        assertRejected(missing, SupervisorProposalRejectReason.MISSING_TRANSITION)
        assertRejected(unknown, SupervisorProposalRejectReason.TRANSITION_NOT_ALLOWED)
    }

    @Test
    fun `slot outside current non secret schema is rejected`() {
        val result = validator.validate(
            observation,
            hypothesis(
                transition = allowedTransition,
                slots = mapOf(TaskGraphSlotId("UNDECLARED") to TaskGraphSlotValue.Text("x")),
            ),
            setOf(allowedSlot),
        )

        assertRejected(result, SupervisorProposalRejectReason.SLOT_NOT_ALLOWED)
    }

    @Test
    fun `authority bearing slot identifiers are rejected even if caller accidentally allowlists them`() {
        val reserved = listOf(
            "SPEECH",
            "TTS",
            "DIAL",
            "TARGET",
            "COMMITMENT",
            "AUTHORIZATION",
            "IDENTITY_VALUE",
            "FACT_VALUE",
            "CREDENTIAL",
        )

        reserved.forEach { value ->
            val id = TaskGraphSlotId(value)
            val result = validator.validate(
                observation,
                hypothesis(
                    transition = allowedTransition,
                    slots = mapOf(id to TaskGraphSlotValue.Text("forbidden")),
                ),
                setOf(id),
            )
            assertRejected(result, SupervisorProposalRejectReason.AUTHORITY_BEARING_SLOT)
        }
    }

    @Test
    fun `low or invalid confidence is rejected before candidate acceptance`() {
        val low = validator.validate(
            observation,
            hypothesis(transition = allowedTransition, confidence = 0.79),
            setOf(allowedSlot),
        )

        assertRejected(low, SupervisorProposalRejectReason.LOW_CONFIDENCE)
    }

    @Test
    fun `validated slots are copied and model mutation cannot widen candidate`() {
        val mutableSlots = linkedMapOf<TaskGraphSlotId, TaskGraphSlotValue>(
            allowedSlot to TaskGraphSlotValue.Text("17:30"),
        )
        val result = validator.validate(
            observation,
            hypothesis(transition = allowedTransition, slots = mutableSlots),
            setOf(allowedSlot),
        ) as SupervisorProposalValidation.Accepted

        mutableSlots[TaskGraphSlotId("TARGET")] = TaskGraphSlotValue.Text("+48123456789")

        assertEquals(setOf(allowedSlot), result.candidate.slotCandidates.keys)
    }

    private fun hypothesis(
        transition: TaskGraphTransitionId?,
        generation: Long = 11L,
        slots: Map<TaskGraphSlotId, TaskGraphSlotValue> = mapOf(
            allowedSlot to TaskGraphSlotValue.Text("17:30"),
        ),
        confidence: Double = 0.91,
    ) = ShadowDialogueHypothesis(
        generation = generation,
        suggestedTransition = transition,
        slotCandidates = slots,
        confidence = confidence,
    )

    private fun assertRejected(
        result: SupervisorProposalValidation,
        reason: SupervisorProposalRejectReason,
    ) {
        assertTrue(result is SupervisorProposalValidation.Rejected)
        assertEquals(reason, (result as SupervisorProposalValidation.Rejected).reason)
    }
}
