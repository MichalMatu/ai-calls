package pl.michalmatu.aicallbridge.dialogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

class DialogueFitContractTest {
    private val policy = DefaultDialogueFitPolicy(maxRecoveryCount = 2)

    @Test
    fun `clean deterministic understanding is high fit`() {
        val result = policy.evaluate(
            signals(
                sttQuality = DialogueSttQuality.GOOD,
                deterministicUnderstanding = DeterministicUnderstanding.MATCHED,
                parserCompleteness = ParserCompleteness.COMPLETE,
                stateCompatibility = StateCompatibility.COMPATIBLE,
                shadowComparison = ShadowComparison.AGREES,
            ),
        )

        assertEquals(DialogueFitLevel.HIGH, result.level)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `uncertain speech or incomplete parsing is explainable uncertainty`() {
        val result = policy.evaluate(
            signals(
                sttQuality = DialogueSttQuality.UNCERTAIN,
                parserCompleteness = ParserCompleteness.PARTIAL,
                missingRequiredSlots = 1,
            ),
        )

        assertEquals(DialogueFitLevel.UNCERTAIN, result.level)
        assertEquals(
            setOf(
                DialogueFitReason.STT_UNCERTAIN,
                DialogueFitReason.PARSER_INCOMPLETE,
                DialogueFitReason.MISSING_REQUIRED_SLOTS,
            ),
            result.reasons.toSet(),
        )
    }

    @Test
    fun `state incompatibility or shadow disagreement lowers fit without granting authority`() {
        val incompatible = policy.evaluate(
            signals(stateCompatibility = StateCompatibility.INCOMPATIBLE),
        )
        val disagreement = policy.evaluate(
            signals(shadowComparison = ShadowComparison.DISAGREES),
        )

        assertEquals(DialogueFitLevel.LOW, incompatible.level)
        assertTrue(DialogueFitReason.STATE_INCOMPATIBLE in incompatible.reasons)
        assertEquals(DialogueFitLevel.LOW, disagreement.level)
        assertTrue(DialogueFitReason.SHADOW_DISAGREEMENT in disagreement.reasons)
    }

    @Test
    fun `contradiction plus exhausted recovery is broken and dominates weaker signals`() {
        val result = policy.evaluate(
            signals(
                sttQuality = DialogueSttQuality.UNCERTAIN,
                parserCompleteness = ParserCompleteness.PARTIAL,
                contradictionDetected = true,
                recoveryCount = 2,
            ),
        )

        assertEquals(DialogueFitLevel.BROKEN, result.level)
        assertTrue(DialogueFitReason.CONTRADICTION in result.reasons)
        assertTrue(DialogueFitReason.RECOVERY_EXHAUSTED in result.reasons)
    }

    @Test
    fun `repeated deterministic unknowns require supervisor proposal before recovery is exhausted`() {
        val result = policy.evaluate(
            signals(
                deterministicUnderstanding = DeterministicUnderstanding.UNKNOWN,
                recoveryCount = 1,
            ),
        )

        assertEquals(DialogueFitLevel.LOW, result.level)
        assertTrue(DialogueFitReason.DETERMINISTIC_UNKNOWN in result.reasons)
        assertTrue(DialogueFitReason.REPEATED_RECOVERY in result.reasons)
    }

    @Test
    fun `shadow observation carries only bounded non secret finalized turn context`() {
        val observation = ShadowDialogueObservation(
            generation = 9L,
            taskGoal = "book appointment",
            state = TaskGraphStateId("WAITING_OFFER"),
            allowedTransitions = setOf(TaskGraphTransitionId("offer-received")),
            validatedSlots = mapOf(
                TaskGraphSlotId("SERVICE") to TaskGraphSlotValue.Text("appointment"),
            ),
            availableFacts = setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.PHONE),
            finalizedTranscript = "Mamy wolny termin w czwartek o 17:30",
        )

        assertEquals(9L, observation.generation)
        assertEquals(setOf(TaskGraphTransitionId("offer-received")), observation.allowedTransitions)
        assertEquals(setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.PHONE), observation.availableFacts)
        assertFalse(observation.toString().contains("12345678901"))
    }

    @Test
    fun `shadow hypothesis is quarantined data and cannot execute a taskgraph transition`() {
        val observation = ShadowDialogueObservation(
            generation = 3L,
            taskGoal = "book appointment",
            state = TaskGraphStateId("WAITING_OFFER"),
            allowedTransitions = setOf(TaskGraphTransitionId("offer-received")),
            validatedSlots = emptyMap(),
            availableFacts = emptySet(),
            finalizedTranscript = "wolny termin o 17:30",
        )
        val observer = ShadowDialogueObserver {
            ShadowDialogueHypothesis(
                generation = it.generation,
                suggestedTransition = TaskGraphTransitionId("offer-received"),
                slotCandidates = mapOf(
                    TaskGraphSlotId("TIME") to TaskGraphSlotValue.Text("17:30"),
                ),
                confidence = 0.91,
                diagnostics = mapOf("source" to "shadow"),
            )
        }

        val hypothesis = observer.observe(observation)

        assertEquals(TaskGraphTransitionId("offer-received"), hypothesis.suggestedTransition)
        assertEquals(3L, hypothesis.generation)
        assertEquals(0.91, hypothesis.confidence, 0.0)
        assertTrue(hypothesis.slotCandidates.containsKey(TaskGraphSlotId("TIME")))
    }

    @Test
    fun `shadow contract rejects authority bearing diagnostics keys`() {
        val unsafeKeys = listOf("speech", "tts", "dial", "target", "commitment", "identity_value")

        unsafeKeys.forEach { key ->
            val failure = runCatching {
                ShadowDialogueHypothesis(
                    generation = 1L,
                    suggestedTransition = null,
                    slotCandidates = emptyMap(),
                    confidence = 0.8,
                    diagnostics = mapOf(key to "forbidden"),
                )
            }
            assertTrue("expected $key to be rejected", failure.isFailure)
        }
    }

    private fun signals(
        sttQuality: DialogueSttQuality = DialogueSttQuality.GOOD,
        deterministicUnderstanding: DeterministicUnderstanding = DeterministicUnderstanding.MATCHED,
        parserCompleteness: ParserCompleteness = ParserCompleteness.COMPLETE,
        stateCompatibility: StateCompatibility = StateCompatibility.COMPATIBLE,
        contradictionDetected: Boolean = false,
        missingRequiredSlots: Int = 0,
        recoveryCount: Int = 0,
        shadowComparison: ShadowComparison = ShadowComparison.NOT_AVAILABLE,
    ) = DialogueFitSignals(
        sttQuality = sttQuality,
        deterministicUnderstanding = deterministicUnderstanding,
        parserCompleteness = parserCompleteness,
        stateCompatibility = stateCompatibility,
        contradictionDetected = contradictionDetected,
        missingRequiredSlots = missingRequiredSlots,
        recoveryCount = recoveryCount,
        shadowComparison = shadowComparison,
    )
}
