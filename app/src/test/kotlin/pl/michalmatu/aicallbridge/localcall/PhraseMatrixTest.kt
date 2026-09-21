package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PhraseMatrixTest {
    @Test
    fun `exact reviewed phrase returns only declared rule id and diagnostics`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "greeting",
                    phrases = setOf("dzień dobry"),
                    variantClass = "GREETING",
                ),
            ),
        )

        val match = matrix.match("  Dzień, dobry!  ")

        assertEquals("greeting", match?.ruleId)
        assertEquals(1.0, match?.confidence ?: 0.0, 0.0)
        assertEquals(PhraseMatcherKind.EXACT, match?.matcherKind)
        assertEquals("GREETING", match?.variantClass)
    }

    @Test
    fun `explicit alias covers reviewed ASR or missing-diacritic variant`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "repeat",
                    phrases = setOf("proszę powtórzyć"),
                    aliases = setOf("prosze powtorzyc", "jeszcze raz"),
                ),
            ),
        )

        val match = matrix.match("PROSZE   POWTORZYC")

        assertEquals("repeat", match?.ruleId)
        assertEquals(1.0, match?.confidence ?: 0.0, 0.0)
        assertEquals(PhraseMatcherKind.ALIAS, match?.matcherKind)
        assertNull(match?.variantClass)
    }

    @Test
    fun `explicit fuzzy phrase tolerates only one reviewed character error`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "repeat",
                    phrases = setOf("proszę powtórzyć"),
                    fuzzyPhrases = setOf("prosze powtorzyc"),
                    variantClass = "ASK_REPEAT",
                ),
            ),
        )

        val match = matrix.match("prosze powturzyc")

        assertEquals("repeat", match?.ruleId)
        assertEquals(0.9, match?.confidence ?: 0.0, 0.0)
        assertEquals(PhraseMatcherKind.FUZZY, match?.matcherKind)
        assertEquals("ASK_REPEAT", match?.variantClass)
        assertNull(matrix.match("prosze powtarzac"))
    }

    @Test
    fun `bounded fuzzy phrase does not absorb negation extra words or another intent`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "repeat",
                    phrases = setOf("proszę powtórzyć"),
                    fuzzyPhrases = setOf("prosze powtorzyc"),
                ),
            ),
        )

        assertNull(matrix.match("nie prosze powturzyc"))
        assertNull(matrix.match("prosze powturzyc i podac cene"))
        assertNull(matrix.match("prosze teraz powturzyc"))
    }

    @Test
    fun `ambiguous fuzzy candidates fail closed`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "repeat-a",
                    phrases = setOf("proszę powtórzyć"),
                    fuzzyPhrases = setOf("prosze powtorzyc"),
                ),
                PhraseMatrixRule(
                    ruleId = "repeat-b",
                    phrases = setOf("proszę powtarzać"),
                    fuzzyPhrases = setOf("prosze powtarzyc"),
                ),
            ),
        )

        assertNull(matrix.match("prosze powtvrzyc"))
    }

    @Test
    fun `short fuzzy sources are rejected instead of fuzzing confirmations`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrix(
                listOf(
                    PhraseMatrixRule(
                        ruleId = "confirm",
                        phrases = setOf("tak"),
                        fuzzyPhrases = setOf("tak"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `previous rule context selects a more specific reviewed meaning`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "ack",
                    phrases = setOf("tak"),
                ),
                PhraseMatrixRule(
                    ruleId = "confirm-time",
                    phrases = setOf("tak"),
                    previousRuleIds = setOf("ask-time"),
                ),
            ),
        )

        assertEquals("ack", matrix.match("tak")?.ruleId)
        assertEquals("ack", matrix.match("tak", previousRuleId = "other")?.ruleId)
        assertEquals("confirm-time", matrix.match("tak", previousRuleId = "ask-time")?.ruleId)
    }

    @Test
    fun `context-only phrase stays unmatched without its declared previous rule`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "confirm-time",
                    phrases = setOf("tak"),
                    previousRuleIds = setOf("ask-time"),
                ),
            ),
        )

        assertNull(matrix.match("tak"))
        assertNull(matrix.match("tak", previousRuleId = "ask-price"))
        assertEquals("confirm-time", matrix.match("tak", previousRuleId = " ask-time ")?.ruleId)
    }

    @Test
    fun `same phrase may use disjoint previous rule contexts but overlapping context fails closed`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule("confirm-time", setOf("tak"), previousRuleIds = setOf("ask-time")),
                PhraseMatrixRule("confirm-price", setOf("tak"), previousRuleIds = setOf("ask-price")),
            ),
        )

        assertEquals("confirm-time", matrix.match("tak", previousRuleId = "ask-time")?.ruleId)
        assertEquals("confirm-price", matrix.match("tak", previousRuleId = "ask-price")?.ruleId)
        assertNull(matrix.match("tak"))

        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrix(
                listOf(
                    PhraseMatrixRule(
                        "confirm-a",
                        setOf("tak"),
                        previousRuleIds = setOf("ask-shared", "ask-a"),
                    ),
                    PhraseMatrixRule(
                        "confirm-b",
                        setOf("TAK!"),
                        previousRuleIds = setOf("ask-shared", "ask-b"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `unknown transcript fails closed without a match`() {
        val matrix = PhraseMatrix(
            listOf(PhraseMatrixRule("greeting", setOf("dzień dobry"))),
        )

        assertNull(matrix.match("chciałbym porozmawiać o czymś innym"))
        assertNull(matrix.match("   "))
    }

    @Test
    fun `normalization collision across rule ids is rejected fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrix(
                listOf(
                    PhraseMatrixRule("confirm", setOf("tak")),
                    PhraseMatrixRule("other", setOf(" TAK! ")),
                ),
            )
        }
    }

    @Test
    fun `exact phrase and alias collision is rejected even within one rule`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrix(
                listOf(
                    PhraseMatrixRule(
                        ruleId = "confirm",
                        phrases = setOf("tak"),
                        aliases = setOf("TAK!"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `same input deterministically replays the same match`() {
        val matrix = PhraseMatrix(
            listOf(
                PhraseMatrixRule(
                    ruleId = "wait",
                    phrases = setOf("proszę poczekać"),
                    aliases = setOf("chwileczkę"),
                    variantClass = "WAIT",
                ),
            ),
        )

        val first = matrix.match("chwileczkę")
        repeat(20) {
            assertEquals(first, matrix.match("chwileczkę"))
        }
    }

    @Test
    fun `rules reject blank ids phrases aliases fuzzy phrases variant classes and previous rule ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule(" ", setOf("tak"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule("confirm", emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule("confirm", setOf(" "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule("confirm", setOf("tak"), aliases = setOf(" "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule("confirm", setOf("tak"), fuzzyPhrases = setOf(" "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule("confirm", setOf("tak"), variantClass = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhraseMatrixRule("confirm", setOf("tak"), previousRuleIds = setOf(" "))
        }

        val matrix = PhraseMatrix(listOf(PhraseMatrixRule("confirm", setOf("tak"))))
        assertThrows(IllegalArgumentException::class.java) {
            matrix.match("tak", previousRuleId = " ")
        }
    }
}
