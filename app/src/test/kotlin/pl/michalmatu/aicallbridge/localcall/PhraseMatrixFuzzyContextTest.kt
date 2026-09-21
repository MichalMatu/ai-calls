package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhraseMatrixFuzzyContextTest {
    @Test
    fun `ambiguous context fuzzy match does not fall through to generic fuzzy`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "generic",
                    phrases = setOf("ogólny fallback"),
                    fuzzyPhrases = setOf("prosze powtvrzyc"),
                ),
                PhraseMatrixRule(
                    ruleId = "context-a",
                    phrases = setOf("wariant a"),
                    fuzzyPhrases = setOf("prosze powtorzyc"),
                    previousRuleIds = setOf("ask-repeat"),
                ),
                PhraseMatrixRule(
                    ruleId = "context-b",
                    phrases = setOf("wariant b"),
                    fuzzyPhrases = setOf("prosze powtarzyc"),
                    previousRuleIds = setOf("ask-repeat"),
                ),
            ),
        )

        assertNull(matrix.match("prosze powtvrzyc", previousRuleId = "ask-repeat"))
    }

    @Test
    fun `unique context fuzzy match overrides generic fuzzy`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "generic",
                    phrases = setOf("ogólny fallback"),
                    fuzzyPhrases = setOf("prosze powtvrzyc"),
                ),
                PhraseMatrixRule(
                    ruleId = "context-repeat",
                    phrases = setOf("powtórz"),
                    fuzzyPhrases = setOf("prosze powtorzyc"),
                    previousRuleIds = setOf("ask-repeat"),
                ),
            ),
        )

        assertEquals(
            "context-repeat",
            matrix.match("prosze powtvrzyc", previousRuleId = "ask-repeat")?.ruleId,
        )
        assertEquals("generic", matrix.match("prosze powtvrzyc")?.ruleId)
    }
}
