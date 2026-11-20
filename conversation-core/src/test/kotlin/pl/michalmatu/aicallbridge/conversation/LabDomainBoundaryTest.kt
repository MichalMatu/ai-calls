package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabDomainBoundaryTest {
    @Test fun offlineDecisionScoringIsPureAndConservative() {
        val metrics = SkillLabScoring.score(listOf(
            SkillLabObservation("case", 1, true, true, "Do widzenia", 20, false),
            SkillLabObservation("case", 2, false, true, "Kiedy?", 40, false),
        ))
        assertEquals(2, metrics.turns)
        assertEquals(1, metrics.accurateEndDecisions)
        assertEquals(1, metrics.prematureEnds)
        assertEquals(0, metrics.missingEnds)
        assertEquals(1, metrics.unexpectedQuestions)
        assertEquals(30L, metrics.meanLatencyMs)
    }

    @Test fun revisionedCaseLibraryRetainsEarlierRevisions() {
        val original = LabCaseRevision(1, "name", "goal", LabRole.INCOMING,
            "Hello", "", "", "", "", "goodbye")
        val first = LabCaseLibrary().create(original)
        val id = first.cases.single().id
        val second = first.update(id, original.copy(title = "new name"))
        assertEquals(listOf(1, 2), second.cases.single().versions.map { it.revision })
        assertEquals("name", second.cases.single().versions.first().title)
        assertEquals("new name", second.cases.single().selected().title)
        assertTrue(second.delete(id).cases.isEmpty())
    }
}
