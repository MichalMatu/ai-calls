package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test

class PhoneTaskSimChoicesTest {
    private val choices = listOf(
        PhoneTaskSimChoice(7, "scope-a", 1, "Orange"),
        PhoneTaskSimChoice(9, "scope-b", 2, "Orange"),
    )

    @Test fun freshDraftUsesExactDefaultVoiceSimAmongIdenticalCarrierNames() {
        assertEquals(2, PhoneTaskSimChoices.selectedIndex(choices, null, null, 9))
        assertEquals(1, PhoneTaskSimChoices.selectedIndex(choices, null, null, 7))
        assertNotEquals(choices[0].slotNumber, choices[1].slotNumber)
    }

    @Test fun savedExactChoiceWinsAndChangedOrMissingSavedSimRequiresSelection() {
        assertEquals(1, PhoneTaskSimChoices.selectedIndex(choices, 7, "scope-a", 9))
        assertEquals(0, PhoneTaskSimChoices.selectedIndex(choices, 7, "old-scope", 9))
        assertEquals(0, PhoneTaskSimChoices.selectedIndex(choices, 99, "missing", 9))
        assertEquals(0, PhoneTaskSimChoices.selectedIndex(choices, 7, null, 9))
    }

    @Test fun absentDefaultDoesNotGuessBetweenMultipleSims() {
        assertEquals(0, PhoneTaskSimChoices.selectedIndex(choices, null, null, -1))
        assertEquals(1, PhoneTaskSimChoices.selectedIndex(choices.take(1), null, null, -1))
        assertEquals(0, PhoneTaskSimChoices.selectedIndex(emptyList(), null, null, -1))
    }
}
