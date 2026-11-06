package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationSkillLibraryTest {
    @Test fun factoryPresetsPinDifferentRevisions() {
        val library = ConversationSkillLibrary.defaults()
        assertEquals(4, library.snapshot().entries.size)
        assertEquals(2, library.snapshot("formal").entries.single { it.id == "ConversationStyle" }.version)
        assertFalse(library.snapshot("message").entries.any { it.id == "ConversationStyle" })
    }

    @Test fun editsVersionSkillsAndLeaveFactoryAndSnapshotsAlone() {
        val custom = ConversationSkillLibrary.defaults().copyActive("Mój preset")
        val old = custom.snapshot()
        val edited = custom.reviseSkill("NaturalClosure", "Zakończ rozmowę po potwierdzeniu sprawy.")
        assertEquals(2, edited.snapshot().entries.single { it.id == "NaturalClosure" }.version)
        assertEquals(1, edited.snapshot("natural").entries.single { it.id == "NaturalClosure" }.version)
        assertEquals(old, custom.snapshot())
        val restored = edited.bindVersion("NaturalClosure", 1)
        assertEquals(1, restored.snapshot().entries.single { it.id == "NaturalClosure" }.version)
        assertEquals(2, restored.skills.single { it.id == "NaturalClosure" }.revisions.size)
    }

    @Test fun factoryIsReadOnlyButCustomCanDisableAndRestore() {
        val factory = ConversationSkillLibrary.defaults()
        assertNotNull(runCatching { factory.reviseSkill("NaturalClosure", "test") }.exceptionOrNull())
        val custom = factory.copyActive("Bez zamknięcia").bindVersion("NaturalClosure", null)
        assertFalse(custom.snapshot().entries.any { it.id == "NaturalClosure" })
        assertEquals(4, custom.snapshot("natural").entries.size)
        assertEquals(4, custom.bindVersion("NaturalClosure", 1).snapshot().entries.size)
    }

    @Test fun serializationKeepsActivePresetAndVersionHistory() {
        var record: String? = null
        fun repo() = ConversationSkillLibraryRepository({ record }, { record = it })
        val first = repo().load().copyActive("Test").reviseSkill("Clarification", "Nowa wersja")
        val old = first.snapshot()
        repo().save(first)
        assertEquals(old, repo().load().snapshot())
        assertEquals("Nowa wersja", old.entries.single { it.id == "Clarification" }.instructions)
        assertEquals(3, repo().load().reviseSkill("Clarification", "Nowsza wersja")
            .snapshot().entries.single { it.id == "Clarification" }.version)
        assertNotNull(runCatching {
            ConversationSkillLibraryRepository({ "{\"schema\":3}" }, {}).load()
        }.exceptionOrNull())
    }

    @Test fun compiledSnapshotStillCarriesAppAuthorityConstraints() {
        val snapshot = ConversationSkillLibrary.defaults().copyActive("Test")
            .reviseSkill("MessageCapture", "Pilnuj korekt.").snapshot()
        val profile = ConversationPreparation.incoming().withSkillSnapshot(snapshot)
        val prompt = ConversationInstructionBuilder.build(profile, "Przyjmij wiadomość", ConversationResponseFormat.TOOL_REPLY)
        assertTrue(prompt.contains("MessageCapture v2"))
        assertTrue(prompt.contains("NaturalClosure v1"))
        assertTrue(prompt.contains("Aplikacja osobno kontroluje telefon"))
        assertFalse(profile.toString().contains("Pilnuj korekt"))
    }
}
