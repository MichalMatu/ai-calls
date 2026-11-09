package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationSkillTurnRoutingTest {
    private val defaults = ConversationSkillLibrary.defaults()

    @Test fun allActiveIsStableAcrossAnyTopicAndNeverRequestsRefresh() {
        val original = defaults.snapshot()
        val router = ConversationSkillTurnRouter(
            original, SkillLoadingMode.ALL_ACTIVE,
            ConversationDirection.UNSPECIFIED, "Rozmowa testowa")
        assertEquals(4, router.initialSelectedIds.size)
        val changed = router.observeUserTurn("Zmiana tematu, popraw wcześniejszą wiadomość")
        val next = router.observeUserTurn("Teraz coś innego")
        assertEquals(listOf(1, 2), listOf(changed.turn, next.turn))
        assertFalse(changed.requiresPromptRefresh)
        assertFalse(next.requiresPromptRefresh)
        assertEquals(original, changed.snapshot)
        assertEquals(original, next.snapshot)
    }

    @Test fun messageSkillActivatesOnSecondTurnAndStaysForFollowup() {
        val router = ConversationSkillTurnRouter(
            defaults.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Prowadź zwykłą rozmowę")
        assertEquals(listOf("ConversationStyle", "NaturalClosure"), router.initialSelectedIds)
        val first = router.observeUserTurn("Przypomnij mi nasze ogólne ustalenia")
        assertFalse(first.requiresPromptRefresh)
        val activate = router.observeUserTurn("Teraz proszę zanotuj wiadomość")
        assertEquals(listOf("MessageCapture"), activate.activatedIds)
        assertTrue(activate.requiresPromptRefresh)
        assertTrue(activate.selectedIds.contains("MessageCapture"))
        val follow = router.observeUserTurn("A jaki był numer faktury?")
        assertTrue(follow.selectedIds.contains("MessageCapture"))
        assertFalse(follow.requiresPromptRefresh)
    }

    @Test fun correctionTracksPreviousMessageUntilExplicitTopicPivot() {
        val router = ConversationSkillTurnRouter(
            defaults.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Test")
        router.observeUserTurn("Przyjmij wiadomość")
        val correction = router.observeUserTurn("Sprostowanie treści, popraw datę")
        assertEquals(listOf("Clarification"), correction.activatedIds)
        assertTrue(correction.selectedIds.containsAll(listOf("Clarification", "MessageCapture")))
        val reset = router.observeUserTurn("Zmienmy temat. Teraz cos innego.")
        assertEquals(listOf("Clarification", "MessageCapture"),
            reset.deactivatedIds.sorted())
        assertEquals(listOf("ConversationStyle", "NaturalClosure"), reset.selectedIds)
        assertTrue(reset.requiresPromptRefresh)
        val next = router.observeUserTurn("Wyjaśnij nowe pytanie")
        assertEquals(listOf("Clarification"), next.activatedIds)
        assertFalse(next.selectedIds.contains("MessageCapture"))
    }

    @Test fun disabledSkillIsNeverResurrectedAndPinnedRevisionSurvives() {
        val library = defaults.copyActive("G4")
            .reviseSkill("ConversationStyle", "Odpowiadaj tekstem fikcyjnym G4.")
            .bindVersion("MessageCapture", null)
        val original = library.snapshot()
        val router = ConversationSkillTurnRouter(
            original, SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Test")
        val decision = router.observeUserTurn("Zapisz wiadomość i popraw datę")
        assertFalse(decision.selectedIds.contains("MessageCapture"))
        assertTrue(decision.selectedIds.contains("Clarification"))
        assertTrue(decision.snapshot.instructions().contains("G4."))
        assertEquals(original, library.snapshot())
        assertFalse(defaults.snapshot().instructions().contains("G4."))
    }

    @Test fun customSkillAlwaysRemainsPresentAcrossTopicChanges() {
        val original = defaults.snapshot().let {
            it.copy(entries = it.entries + SkillSnapshotEntry(
                "custom", "Własny test", SkillSection.MESSAGE, 5, "Instrukcja wersja 5"))
        }
        val router = ConversationSkillTurnRouter(
            original, SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Test")
        val a = router.observeUserTurn("Zapisz wiadomość")
        val b = router.observeUserTurn("Inny temat, proszę")
        assertTrue(a.selectedIds.contains("custom"))
        assertTrue(b.selectedIds.contains("custom"))
        assertEquals(5, b.snapshot.entries.single { it.id == "custom" }.version)
        assertTrue(b.deactivatedIds.contains("MessageCapture"))
    }

    @Test fun repeatDoesNotAccidentallyRequestEngineRebuild() {
        val router = ConversationSkillTurnRouter(
            defaults.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.OUTGOING, "Test")
        val first = router.observeUserTurn("Popraw datę")
        val next = router.observeUserTurn("Jeszcze raz popraw datę")
        assertTrue(first.requiresPromptRefresh)
        assertFalse(next.requiresPromptRefresh)
        assertTrue(next.activatedIds.isEmpty())
        assertTrue(next.deactivatedIds.isEmpty())
        assertEquals(listOf("Clarification"), first.activatedIds)
    }
}
