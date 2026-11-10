package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class ConversationPreparationDataScopeTest {
    @Test fun configuredReferencesAreOnlyAnUpperBoundForTaskSpecificAuthorization() {
        val baseline = ConversationPreparation.outgoing()
        val profile = baseline.withSettings(
            "Zapytaj o koszt usługi.", baseline.skills,
            setOf(IdentityFieldId.PHONE, IdentityFieldId.EMAIL),
        )
        assertEquals(setOf(IdentityFieldId.PHONE),
            profile.scopedDataFields(setOf(IdentityFieldId.PHONE, IdentityFieldId.PESEL)))
        assertTrue(profile.scopedDataFields(emptySet()).isEmpty())
        assertTrue(profile.scopedDataFields(setOf(IdentityFieldId.PESEL)).isEmpty())
        assertEquals("Zapytaj o koszt usługi.", profile.notes)
        assertEquals(setOf(IdentityFieldId.PHONE, IdentityFieldId.EMAIL), profile.dataFields)
    }

    @Test fun separateDirectionsAndLegacySkillsPersistWhenEditingOnlyContextAndScope() {
        val data = mutableMapOf<String, String>()
        val repo = ConversationPreparationRepository(
            { id -> data[id] },
            { id, value -> data[id] = value },
        )
        val incoming = repo.load(ConversationPreparation.INCOMING_ID)
        val outgoing = repo.load(ConversationPreparation.OUTGOING_ID)
        repo.save(incoming.withSettings("Powiedz, że właściciel jest zajęty.",
            incoming.skills, setOf(IdentityFieldId.FIRST_NAME)))
        repo.save(outgoing.withSettings("Sprawdź terminy.", outgoing.skills,
            setOf(IdentityFieldId.EMAIL)))
        val reloadedIncoming = repo.load(ConversationPreparation.INCOMING_ID)
        val reloadedOutgoing = repo.load(ConversationPreparation.OUTGOING_ID)
        assertEquals(incoming.skills, reloadedIncoming.skills)
        assertEquals(outgoing.skills, reloadedOutgoing.skills)
        assertEquals(setOf(IdentityFieldId.FIRST_NAME), reloadedIncoming.dataFields)
        assertEquals(setOf(IdentityFieldId.EMAIL), reloadedOutgoing.dataFields)
        assertEquals("Powiedz, że właściciel jest zajęty.", reloadedIncoming.notes)
        assertEquals("Sprawdź terminy.", reloadedOutgoing.notes)
        assertNotEquals(reloadedIncoming.id, reloadedOutgoing.id)
    }

    @Test fun modelInstructionIncludesContextButOnlyFieldIdentifiersWithoutVaultValues() {
        val baseline = ConversationPreparation.outgoing()
        val scoped = baseline.withSettings("Zapytaj o dostępność.",
            baseline.skills, setOf(IdentityFieldId.EMAIL, IdentityFieldId.PESEL))
        val text = ConversationInstructionBuilder.build(
            scoped, "Ustal koszt", ConversationResponseFormat.SPOKEN_TEXT)
        assertTrue(text.contains("Zapytaj o dostępność."))
        assertTrue(text.contains("EMAIL"))
        assertTrue(text.contains("PESEL"))
        assertTrue(text.contains("referencje, nie wartości"))
    }
}
