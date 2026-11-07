package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationSkillLabTest {
    @Test fun sameCorpusCoversCorrectionsAdditionalBusinessAndFinality() {
        val all = SkillLabCorpus.stories.flatMap { it.turns }
        assertTrue(SkillLabCorpus.stories.size >= 3)
        assertTrue(all.count { it.shouldEnd } >= 3)
        assertTrue(all.count { !it.shouldEnd } >= 5)
        assertTrue(all.any { "poprawk" in it.utterance })
        assertTrue(all.any { "drugą sprawę" in it.utterance })
    }

    @Test fun noQuestionMarkRequestStillRequiresHumanReview() {
        val actual = listOf(
            SkillLabObservation("rower", 1, false, false,
                "Proszę podać numer telefonu i adres.", 125L, false),
            SkillLabObservation("rower", 2, false, false,
                "Zapisano wiadomość.", 175L, false),
            SkillLabObservation("inne", 1, false, false,
                "Podaj dodatkowe dane", 200L, null),
        )
        assertTrue(SkillLabScoring.mayNeedQuestionReview(actual[0].reply))
        assertEquals(1, SkillLabScoring.score(actual).unexpectedQuestions)
        assertEquals(3, SkillLabScoring.score(actual).turns)
    }

    @Test fun scoringSeparatesFalseEndsMissingEndsLatencyAndHumanRating() {
        val observations = listOf(
            SkillLabObservation("test", 1, false, false, "Rozumiem.", 100, false),
            SkillLabObservation("test", 2, false, true, "Do widzenia.", 200, false),
            SkillLabObservation("test", 3, true, false, "Czy coś jeszcze?", 300, false),
            SkillLabObservation("test", 4, true, true, "Do widzenia.", 400, false),
        )
        val score = SkillLabScoring.score(observations)
        assertEquals(4, score.turns)
        assertEquals(2, score.accurateEndDecisions)
        assertEquals(1, score.prematureEnds)
        assertEquals(1, score.missingEnds)
        assertEquals(1, score.unexpectedQuestions)
        assertEquals(250L, score.meanLatencyMs)
        assertTrue(score.describe().contains("nieprzyznana"))
        assertTrue(score.describe(5).contains("5/5"))
        assertEquals(0L, SkillLabScoring.score(emptyList()).meanLatencyMs)
    }
}
