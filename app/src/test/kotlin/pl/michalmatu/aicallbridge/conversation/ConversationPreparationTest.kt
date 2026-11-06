package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class ConversationPreparationTest {
    @Test fun incomingInstructionsDescribeAnsweringRatherThanCallingOrange() {
        val profile = ConversationPreparation.incoming()
        val prompt = ConversationInstructionBuilder.build(profile, "Przyjmij temat rozmowy", ConversationResponseFormat.SPOKEN_TEXT)
        assertTrue(prompt.contains("ODBIERAJĄCĄ"))
        assertFalse(prompt.contains("DZWONIĄCYM"))
        assertFalse(prompt.contains("CLIR"))
        assertTrue(prompt.contains("Przyjmij temat rozmowy"))
    }

    @Test fun outgoingAndIncomingContextRemainSeparateAcrossStoreRecreation() {
        val records = mutableMapOf<String, String>()
        val first = ConversationPreparationRepository(records::get, { k, v -> records[k] = v })
        first.save(ConversationPreparation.outgoing().withSettings("Godziny 9–17", setOf(ConversationSkill.CLARIFY), setOf(IdentityFieldId.PHONE)))
        first.save(ConversationPreparation.incoming().withSettings("Przyjmij wiadomość", emptySet(), emptySet()))
        val reopened = ConversationPreparationRepository(records::get, { k, v -> records[k] = v })
        assertEquals("Godziny 9–17", reopened.load(ConversationPreparation.OUTGOING_ID).notes)
        assertEquals("Przyjmij wiadomość", reopened.load(ConversationPreparation.INCOMING_ID).notes)
        assertEquals(emptySet<ConversationSkill>(), reopened.load(ConversationPreparation.INCOMING_ID).skills)
    }

    @Test fun preparedSkillsAndReferencesDoNotChangeAfterEditingTheSourceCollections() {
        val skills = mutableSetOf(ConversationSkill.CLARIFY)
        val fields = mutableSetOf(IdentityFieldId.PHONE)
        val prepared = ConversationPreparation.outgoing().withSettings("", skills, fields)
        skills.clear(); fields.add(IdentityFieldId.PESEL)
        assertEquals(setOf(ConversationSkill.CLARIFY), prepared.skills)
        assertEquals(setOf(IdentityFieldId.PHONE), prepared.dataFields)
    }

    @Test fun contextsAndPromptsDoNotExposeNotesInDiagnosticDescriptions() {
        val profile = ConversationPreparation.incoming().withSettings("private-context-fixture", emptySet(), emptySet())
        assertFalse(profile.toString().contains("private-context-fixture"))
        assertTrue(ConversationInstructionBuilder.build(profile, "Cel", ConversationResponseFormat.SPOKEN_TEXT).contains("private-context-fixture"))
    }

    @Test fun selectingDataReferencesCannotWidenTaskDisclosureAuthority() {
        val profile = ConversationPreparation.incoming().withSettings("", emptySet(), setOf(IdentityFieldId.PHONE, IdentityFieldId.PESEL))
        assertEquals(setOf(IdentityFieldId.PHONE), profile.scopedDataFields(setOf(IdentityFieldId.PHONE)))
        assertTrue(profile.scopedDataFields(emptySet()).isEmpty())
    }

    @Test fun corruptStoredContextIsReportedRatherThanSilentlyUsingDefaults() {
        val repository = ConversationPreparationRepository({ "malformed" }, { _, _ -> })
        assertNotNull(runCatching { repository.load(ConversationPreparation.OUTGOING_ID) }.exceptionOrNull())
    }
}
