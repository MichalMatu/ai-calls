package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LabDialogueReportTest {
    private fun report(id: String, model: String) = LabDialogueReport(id,
        "case-a", 1, model, "skill-v1",
        listOf(LabReportTurn(1, "User text", "Model text", false, 20),
            LabReportTurn(2, "Correction", "Updated", true, 40)),
        listOf(LabAdaptiveTrace(1, 2, "question", "Correction")),
        LabReviewDimension.entries.map { LabReviewEvidence(it,
            LabReviewVerdict.UNASSESSED, null, "") }, false)

    @Test fun semanticQualityNeverAutomaticallyPasses() {
        val r = report("a", "gemma")
        assertEquals(30, r.meanLatency())
        assertTrue(r.reviews.all { it.verdict == LabReviewVerdict.UNASSESSED })
        assertTrue(r.evidenceText().contains("END_CALL proposals: 1"))
        val changed = r.review(LabReviewDimension.MEMORY, LabReviewVerdict.FAIL, 2,
            "Forgot the earlier correction")
        assertEquals(LabReviewVerdict.FAIL,
            changed.reviews.first { it.dimension == LabReviewDimension.MEMORY }.verdict)
        assertTrue(changed.evidenceText().contains("[turn 2]"))
        try { r.review(LabReviewDimension.TOPIC, LabReviewVerdict.PASS, null, ""); fail() }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun saveRestoreComparisonAndReview() {
        val library = LabReportLibrary().add(report("a", "model-a")).add(report("b", "model-b"))
        val saved = Gson().fromJson(Gson().toJson(library), LabReportLibrary::class.java)
        val compared = saved.compare("a", "b")
        assertTrue(compared.comparable)
        assertEquals(2, compared.aTurns)
        assertEquals(2, compared.bTurns)
        assertEquals(30, compared.bLatencyMs)
        val updated = saved.review("a", LabReviewDimension.CORRECTIONS,
            LabReviewVerdict.PASS, 2, "Applied correction")
        assertEquals(LabReviewVerdict.PASS, updated.reports.first().reviews.first {
            it.dimension == LabReviewDimension.CORRECTIONS }.verdict)
        try { saved.compare("a", "a"); fail() }
        catch (_: IllegalArgumentException) {}
        assertFalse(LabABComparison.between(
            report("a", "model-a"), report("b", "model-b").copy(testRevision = 2)).comparable)
    }
}
