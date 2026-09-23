package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseResponseTemperatureTest {
    @Test
    fun `minor natural wording variation stays warm`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "confirm-current-line",
                    phrases = setOf("czy sprawa dotyczy numeru z którego dzwonisz"),
                ),
            ),
        )

        val assessment = matrix.assessResponseTemperature(
            "czy ta sprawa dotyczy numeru z którego teraz dzwonisz",
        )

        assertEquals(PhraseResponseTemperatureBand.WARM, assessment.band)
        assertEquals("confirm-current-line", assessment.ruleId)
        assertTrue(assessment.confidence >= 0.70)
    }

    @Test
    fun `bounded paraphrase is uncertain instead of hard miss`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "confirm-current-line",
                    phrases = setOf("czy sprawa dotyczy numeru z którego dzwonisz"),
                ),
            ),
        )

        val assessment = matrix.assessResponseTemperature(
            "czy chodzi o numer z którego dzwonisz",
        )

        assertTrue(
            assessment.band == PhraseResponseTemperatureBand.WARM ||
                assessment.band == PhraseResponseTemperatureBand.UNCERTAIN,
        )
        assertEquals("confirm-current-line", assessment.ruleId)
    }

    @Test
    fun `similar competing intents stay ambiguous and fail closed`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "phone-number",
                    phrases = setOf("czy chodzi o ten numer telefonu"),
                ),
                PhraseMatrixRule(
                    ruleId = "invoice-number",
                    phrases = setOf("czy chodzi o ten numer faktury"),
                ),
            ),
        )

        val assessment = matrix.assessResponseTemperature("czy chodzi o ten numer")

        assertEquals(PhraseResponseTemperatureBand.AMBIGUOUS, assessment.band)
        assertEquals(null, assessment.ruleId)
        assertTrue(assessment.candidateRuleIds.containsAll(setOf("phone-number", "invoice-number")))
    }

    @Test
    fun `unrelated utterance remains cold`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "confirm-current-line",
                    phrases = setOf("czy sprawa dotyczy numeru z którego dzwonisz"),
                ),
            ),
        )

        val assessment = matrix.assessResponseTemperature("proszę podać kod klienta")

        assertEquals(PhraseResponseTemperatureBand.COLD, assessment.band)
        assertEquals(null, assessment.ruleId)
        assertNotNull(assessment.reason)
    }
}
