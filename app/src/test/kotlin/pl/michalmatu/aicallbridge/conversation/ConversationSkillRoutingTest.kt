package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationSkillRoutingTest {
    private val default = ConversationSkillLibrary.defaults()

    @Test fun staticModePreservesOriginalPromptByteForByte() {
        val snapshot = default.snapshot()
        val routed = ConversationSkillRouter.route(
            snapshot, SkillLoadingMode.ALL_ACTIVE, ConversationDirection.UNSPECIFIED,
            "Dialog testowy")
        assertSame(snapshot, routed.snapshot)
        assertEquals(snapshot.instructions(), routed.snapshot.instructions())
        assertEquals(4, routed.selectedIds.size)
    }

    @Test fun genericLabSessionSelectsStyleAndClosureOnly() {
        val result = ConversationSkillRouter.route(
            default.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Prowadź naturalny dialog testowy")
        assertEquals(listOf("ConversationStyle", "NaturalClosure"), result.selectedIds)
        assertEquals(4, result.availableCount)
        assertFalse(result.snapshot.instructions().contains("MessageCapture"))
        assertFalse(result.snapshot.instructions().contains("Clarification"))
    }

    @Test fun incomingAndRelevantGoalsRetainContextualSkills() {
        val incoming = ConversationSkillRouter.route(
            default.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.INCOMING, "Odbierz rozmowę")
        assertEquals(4, incoming.selectedIds.size)
        val goal = ConversationSkillRouter.route(
            default.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.OUTGOING, "Zapisz wiadomość i wyjaśnij korektę")
        assertTrue(goal.selectedIds.contains("MessageCapture"))
        assertTrue(goal.selectedIds.contains("Clarification"))
    }

    @Test fun disabledInstructionsStayDisabledAndPinnedRevisionsRemainUnchanged() {
        val library = default.copyActive("AB").reviseSkill(
            "ConversationStyle", "Wypowiadaj neutralne słowo KASZTAN.")
            .bindVersion("MessageCapture", null)
        val before = library.snapshot()
        val result = ConversationSkillRouter.route(
            before, SkillLoadingMode.CONTEXTUAL, ConversationDirection.INCOMING,
            "Przyjmij wiadomość")
        assertFalse(result.selectedIds.contains("MessageCapture"))
        assertTrue(result.snapshot.instructions().contains("KASZTAN"))
        assertEquals(before, library.snapshot())
        assertFalse(default.snapshot().instructions().contains("KASZTAN"))
    }

    @Test fun unknownCustomSkillIsNeverSilentlyDropped() {
        val original = default.snapshot()
        val custom = original.copy(entries = original.entries + SkillSnapshotEntry(
            "custom-1", "Własna umiejętność", SkillSection.MESSAGE, 2, "Tekst syntetyczny"))
        val result = ConversationSkillRouter.route(
            custom, SkillLoadingMode.CONTEXTUAL, ConversationDirection.UNSPECIFIED, "Test")
        assertTrue(result.selectedIds.contains("custom-1"))
    }

    @Test fun contextualPromptStillIncludesAllApplicationSafetyBoundaries() {
        val selected = ConversationSkillRouter.route(
            default.snapshot(), SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Test").snapshot
        val prepared = ConversationPreparation.supervised().withSkillSnapshot(selected)
        val prompt = ConversationInstructionBuilder.build(
            prepared, "Test", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(prompt.contains("Aplikacja osobno kontroluje telefon"))
        assertTrue(prompt.contains("Nie przyznają"))
        assertTrue(prompt.indexOf("OGRANICZENIA:") <
            prompt.indexOf("WERSJONOWANE SKILLS WYBRANEGO PRESETU"))
        assertTrue(prompt.indexOf("WERSJONOWANE SKILLS WYBRANEGO PRESETU") <
            prompt.indexOf("ODPOWIEDŹ:"))
    }
}
