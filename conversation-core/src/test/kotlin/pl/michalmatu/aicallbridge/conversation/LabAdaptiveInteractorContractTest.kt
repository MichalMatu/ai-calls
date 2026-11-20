package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class LabAdaptiveInteractorContractTest {
    private fun fixture(rules: String) = LabCaseRevision(
        1, "isolated", "offline neutral dialogue", LabRole.GENERAL,
        "hello", "", "", "", rules, "stop")

    @Test fun questionBranchIsSingleUse() {
        val lab = LabAdaptiveInteractor(fixture("question => Ask details"))
        assertEquals("hello", lab.firstMessage())
        val answer = lab.next("What happened?", false)
        assertNotNull(answer)
        assertEquals("question", answer!!.condition)
        assertEquals(1, answer.ruleNumber)
        assertEquals("Ask details", answer.utterance)
        assertNull(lab.next("Another question?", false))
    }

    @Test fun matchContainsAndMissingAreDeterministic() {
        val lab = LabAdaptiveInteractor(fixture(
            "contains:hello => Hello found\nmissing:world => World missing"))
        assertEquals("Hello found", lab.next("HELLO friend", false)?.utterance)
        assertEquals("World missing", lab.next("no matching word", false)?.utterance)
        assertNull(lab.next("one more",false))
    }

    @Test fun endingAndRefusalHaveExplicitBranches() {
        val lab = LabAdaptiveInteractor(fixture(
            "end => goodbye\nrefusal => clarify"))
        assertEquals("clarify", lab.next("Nie mogę zrobić tego",false)?.utterance)
        assertEquals("goodbye", lab.next("Do widzenia",true)?.utterance)
        assertNull(lab.next("Do widzenia",true))
    }

    @Test fun invalidRuleFailsClosed() {
        try {
            LabAdaptiveInteractor(fixture("unknown => script"))
            fail("invalid conditional rule accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("lab_adaptive_condition_unknown"))
        }
    }
}
