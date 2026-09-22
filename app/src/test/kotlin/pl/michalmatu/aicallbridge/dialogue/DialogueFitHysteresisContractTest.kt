package pl.michalmatu.aicallbridge.dialogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-only sequence contract: hysteresis never grants TaskGraph or output authority. */
class DialogueFitHysteresisContractTest {
    private val policy = DefaultDialogueFitPolicy(maxRecoveryCount = 2)

    @Test
    fun `safety deterioration applies immediately`() {
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)

        val clean = hysteresis.update(policy.evaluate(signals()))
        val contradiction = hysteresis.update(
            policy.evaluate(signals(contradictionDetected = true)),
        )

        assertEquals(DialogueFitLevel.HIGH, clean.effective.level)
        assertEquals(DialogueFitLevel.LOW, contradiction.raw.level)
        assertEquals(DialogueFitLevel.LOW, contradiction.effective.level)
        assertFalse(contradiction.heldForRecovery)
        assertEquals(0, contradiction.recoveryEvidenceCount)
    }

    @Test
    fun `one clean turn cannot immediately erase low fit`() {
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)
        hysteresis.update(policy.evaluate(signals(contradictionDetected = true)))

        val firstClean = hysteresis.update(policy.evaluate(signals()))

        assertEquals(DialogueFitLevel.HIGH, firstClean.raw.level)
        assertEquals(DialogueFitLevel.LOW, firstClean.effective.level)
        assertTrue(firstClean.heldForRecovery)
        assertEquals(1, firstClean.recoveryEvidenceCount)
        assertTrue(DialogueFitReason.CONTRADICTION in firstClean.effective.reasons)
    }

    @Test
    fun `configured consecutive clean evidence releases recovery without numeric scoring`() {
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)
        hysteresis.update(policy.evaluate(signals(contradictionDetected = true)))

        hysteresis.update(policy.evaluate(signals()))
        val secondClean = hysteresis.update(policy.evaluate(signals()))

        assertEquals(DialogueFitLevel.HIGH, secondClean.raw.level)
        assertEquals(DialogueFitLevel.HIGH, secondClean.effective.level)
        assertFalse(secondClean.heldForRecovery)
        assertEquals(0, secondClean.recoveryEvidenceCount)
    }

    @Test
    fun `changing recovery target resets evidence instead of mixing unlike turns`() {
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)
        hysteresis.update(
            policy.evaluate(
                signals(
                    deterministicUnderstanding = DeterministicUnderstanding.UNKNOWN,
                    recoveryCount = 1,
                ),
            ),
        )

        val uncertain = hysteresis.update(
            policy.evaluate(signals(sttQuality = DialogueSttQuality.UNCERTAIN)),
        )
        val high = hysteresis.update(policy.evaluate(signals()))

        assertEquals(DialogueFitLevel.LOW, uncertain.effective.level)
        assertEquals(1, uncertain.recoveryEvidenceCount)
        assertEquals(DialogueFitLevel.LOW, high.effective.level)
        assertEquals(1, high.recoveryEvidenceCount)
    }

    @Test
    fun `same or worse turn cancels pending recovery evidence`() {
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)
        hysteresis.update(policy.evaluate(signals(contradictionDetected = true)))
        hysteresis.update(policy.evaluate(signals()))

        val lowAgain = hysteresis.update(
            policy.evaluate(
                signals(
                    deterministicUnderstanding = DeterministicUnderstanding.UNKNOWN,
                    recoveryCount = 1,
                ),
            ),
        )
        val cleanAgain = hysteresis.update(policy.evaluate(signals()))

        assertEquals(DialogueFitLevel.LOW, lowAgain.effective.level)
        assertEquals(0, lowAgain.recoveryEvidenceCount)
        assertEquals(DialogueFitLevel.LOW, cleanAgain.effective.level)
        assertEquals(1, cleanAgain.recoveryEvidenceCount)
    }

    @Test
    fun `reset prevents hysteresis state from crossing a session or generation boundary`() {
        val hysteresis = DialogueFitHysteresis(requiredRecoveryEvidence = 2)
        hysteresis.update(policy.evaluate(signals(contradictionDetected = true)))
        hysteresis.update(policy.evaluate(signals()))

        hysteresis.reset()
        val newSession = hysteresis.update(policy.evaluate(signals()))

        assertEquals(DialogueFitLevel.HIGH, newSession.effective.level)
        assertFalse(newSession.heldForRecovery)
        assertEquals(0, newSession.recoveryEvidenceCount)
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
