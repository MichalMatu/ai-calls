package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class LabAdaptiveInteractorTest {
    private fun test(rules: String) = LabCaseRevision(1, "Test", "Goal",
        LabRole.GENERAL, "Opening", "", "", "", rules, "Complete")
    @Test fun actualAnswerControlsBranchAndEachRuleCanOnlyBeUsedOnce() {
        val rules = "question => More detail\nend => Not yet\ncontains:blue => Correction\notherwise => Continue"
        val asks = LabAdaptiveInteractor(test(rules))
        val states = LabAdaptiveInteractor(test(rules))
        assertEquals("Opening", asks.firstMessage())
        assertEquals("More detail", asks.next("Which date?", false)?.utterance)
        assertEquals("Correction", asks.next("Blue", false)?.utterance)
        assertEquals("Not yet", asks.next("Bye", true)?.utterance)
        assertEquals("Continue", asks.next("Other reply", false)?.utterance)
        assertNull(asks.next("Any text", false))
        assertEquals("Correction", states.next("It's blue", false)?.utterance)
        assertEquals(3, states.next("Blue again", false)?.ruleNumber)
        // The exhausted contains rule cannot repeat even if the same reply repeats.
        assertEquals("Continue", states.next("Blue again", false)?.utterance)
    }
    @Test fun malformedRulesFailBeforeAnyAutoplay() {
        try { LabAdaptiveInteractor(test("question do something")); fail() }
        catch (_: IllegalArgumentException) {}
        try { LabAdaptiveInteractor(test("mystery => text")); fail() }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun missingFactAndRefusalUseObservedWords() {
        val interactor = LabAdaptiveInteractor(test(
            "missing:confirmed => Clarify\nrefusal => Ask alternative"))
        assertEquals("Clarify", interactor.next("Pending", false)?.utterance)
        assertEquals("Ask alternative", interactor.next("Nie mogę tego zrobić", false)?.utterance)
        assertNull(interactor.next("Done", false))
    }
}
