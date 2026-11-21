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
            LabReviewVerdict.UNASSESSED, null, "") }, false,
        terminalPhase = SkillLabPhase.COMPLETED)

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

    @Test fun reportKeepsActualSessionConfigurationAndSupportsOlderReports() {
        val config = LabRunConfiguration("model-snapshot", 6144, 768, 0.2, 12, 0.9,
            "pl-PL", SkillLoadingMode.ALL_ACTIVE, "a".repeat(64))
        val result = LabDialogueReport.fromSnapshot("case-a", 2, "stale-ui-model", "natural",
            SkillLabUiState(phase = SkillLabPhase.STOPPED, configuration = config))
        assertEquals("model-snapshot", result.modelId)
        val gson = Gson()
        assertEquals(config, gson.fromJson(gson.toJson(result), LabDialogueReport::class.java).configuration)
        assertTrue(result.evidenceText().contains("Context: 6144"))
        val legacy = gson.toJsonTree(result).asJsonObject.apply { remove("configuration") }
        assertNull(gson.fromJson(legacy, LabDialogueReport::class.java).configuration)
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

    @Test fun incompleteDivergentAndLegacyRunsCannotClaimComparableInputs() {
        val reference = report("a", "model-a")
        val candidate = report("b", "model-b")
        val failed = candidate.copy(terminalPhase = SkillLabPhase.FAILED)
        assertTrue(failed.evidenceText().contains("Outcome: FAILED"))
        assertFalse(LabABComparison.between(reference, failed).comparable)
        assertFalse(LabABComparison.between(reference,
            candidate.copy(turns = candidate.turns.map { it.copy(caller = "Alternative") })
        ).comparable)
        val oldJson = Gson().toJsonTree(candidate).asJsonObject.apply { remove("terminalPhase") }
        val legacy = Gson().fromJson(oldJson, LabDialogueReport::class.java)
        assertTrue(legacy.evidenceText().contains("Outcome: UNKNOWN"))
        assertFalse(LabABComparison.between(reference, legacy).comparable)
    }
}
