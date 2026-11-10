package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ReceptionistToneAndInstructionsTest {
    private fun fresh() = ConversationSkillLibrary.defaults()

    @Test fun factoryTonesHaveDifferentSelectedRevisionsWithoutRewritingHistory() {
        val source = fresh()
        val formal = source.withReceptionistTone(2)
        assertEquals("formal", formal.activePresetId)
        assertEquals(2, formal.receptionistToneVersion())
        assertEquals(1, source.receptionistToneVersion())
        assertEquals("natural", formal.withReceptionistTone(1).activePresetId)
        assertEquals(3, formal.presets.size)
    }

    @Test fun firstPersonalInstructionsSaveCreatesNamedInternallyButDoesNotEditFactory() {
        val original = fresh()
        val result = original.withPersonalInstructions(
            "Używaj krótkich zdań.", "Własne ustawienia")
        assertFalse(result.active.isFactory)
        assertEquals("Własne ustawienia", result.active.name)
        assertEquals(4, original.snapshot().entries.size)
        assertEquals(4, result.snapshot("natural").entries.size)
        assertEquals("Używaj krótkich zdań.", result.personalInstructions())
        assertTrue(result.snapshot().entries.any {
            it.id == ConversationSkillLibrary.PERSONAL_INSTRUCTIONS_ID
        })
    }

    @Test fun changingTonePreservesPersonalInstructionsAndOtherActiveSettings() {
        val edited = fresh().withPersonalInstructions("Nie pytaj o PESEL.", "Auto")
            .bindVersion("NaturalClosure", null)
        val formal = edited.withReceptionistTone(2)
        assertEquals(2, formal.receptionistToneVersion())
        assertEquals("Nie pytaj o PESEL.", formal.personalInstructions())
        assertFalse(formal.snapshot().entries.any { it.id == "NaturalClosure" })
        assertTrue(formal.snapshot("natural").entries.any { it.id == "NaturalClosure" })
        assertEquals(edited.activePresetId, formal.activePresetId)
    }

    @Test fun personalGuidanceVersionsSurviveRoundTripAndCanBeClearedWithoutDeletingHistory() {
        var serialized: String? = null
        fun repository() = ConversationSkillLibraryRepository(
            { serialized }, { serialized = it })
        val first = fresh().withPersonalInstructions("Prosty język.", "Auto")
            .withPersonalInstructions("Krótkie zdania.", "Auto")
        repository().save(first)
        val loaded = repository().load()
        assertEquals("Krótkie zdania.", loaded.personalInstructions())
        val history = loaded.skills.single {
            it.id == ConversationSkillLibrary.PERSONAL_INSTRUCTIONS_ID
        }
        assertEquals(2, history.revisions.size)
        val cleared = loaded.withPersonalInstructions("    ", "Auto")
        assertEquals("", cleared.personalInstructions())
        assertTrue(cleared.skills.any { it.id == ConversationSkillLibrary.PERSONAL_INSTRUCTIONS_ID })
        assertEquals("Prosty język.", cleared.bindVersion(
            ConversationSkillLibrary.PERSONAL_INSTRUCTIONS_ID, 1).personalInstructions())
    }

    @Test fun legacyMessagePresetAndCustomStyleRemainUntilUserChangesTone() {
        val legacy = fresh().select("message")
        assertEquals(0, legacy.receptionistToneVersion())
        val migrated = legacy.withReceptionistTone(1)
        assertEquals("natural", migrated.activePresetId)
        val custom = fresh().copyActive("Dotychczasowy")
            .reviseSkill("ConversationStyle", "Specjalny styl.")
        assertEquals(3, custom.receptionistToneVersion())
        assertEquals(2, custom.withReceptionistTone(2).receptionistToneVersion())
        assertEquals(3, custom.receptionistToneVersion())
    }

    @Test fun incomingPromptUsesSelectedToneButDoesNotGrantAuthority() {
        val formal = fresh().withPersonalInstructions(
            "Odpowiadaj jednozdaniowo.", "Auto").withReceptionistTone(2)
        val incoming = ConversationPreparation.incoming()
            .withSkillSnapshot(formal.snapshot())
        val incomingPrompt = ConversationInstructionBuilder.build(
            incoming, "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(incomingPrompt.contains("Zachowaj formalny, uprzejmy"))
        assertTrue(incomingPrompt.contains("Odpowiadaj jednozdaniowo."))
        assertTrue(incomingPrompt.contains("Aplikacja osobno kontroluje telefon"))
        assertFalse(incomingPrompt.contains("Mów naturalnie, neutralnie"))
        val outgoingPrompt = ConversationInstructionBuilder.build(
            ConversationPreparation.outgoing(), "Umów wizytę",
            ConversationResponseFormat.SPOKEN_TEXT)
        assertFalse(outgoingPrompt.contains("Odpowiadaj jednozdaniowo."))
    }

    @Test fun invalidInstructionsAreRejectedWithoutCreatingPreset() {
        val factory = fresh()
        val oversized = "x".repeat(ConversationSkillLibrary.MAX_PERSONAL_INSTRUCTIONS_LENGTH+1)
        assertThrows(IllegalArgumentException::class.java) {
            factory.withPersonalInstructions(oversized, "Auto")
        }
        assertSame(factory, factory.withPersonalInstructions("  ", "Auto"))
    }
}
