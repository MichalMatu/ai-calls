package pl.michalmatu.aicallbridge.conversation

import org.junit.Assert.*
import org.junit.Test

class ConversationSkillMetadataTest {
    @Test fun legacyEncryptedSchemaOneAutoDerivesCapabilitiesWithoutChangingBindings() {
        var encryptedRecord: String? = null
        val repo = ConversationSkillLibraryRepository(
            { encryptedRecord }, { encryptedRecord = it })
        val legacy = ConversationSkillLibrary.defaults()
        repo.save(legacy)
        encryptedRecord = encryptedRecord!!.replace(Regex(",?\\\"metadata\\\":null"), "")
        val loaded = repo.load()
        assertEquals(legacy.snapshot().entries.map { it.id }, loaded.snapshot().entries.map { it.id })
        assertEquals(SkillCapability.MESSAGE_CAPTURE,
            loaded.snapshot().entries.single { it.id == "MessageCapture" }.capability)
        assertEquals(SkillCapability.CLARIFICATION,
            loaded.snapshot().entries.single { it.id == "Clarification" }.capability)
    }

    @Test fun nameDescriptionAndCapabilityArePinnedByRevisionAndRollback() {
        val original = ConversationSkillLibrary.defaults().copyActive("G4.3 test")
        val old = original.snapshot()
        val metadata = SkillMetadata("Przyjmij zgłoszenie",
            "Tylko fikcyjne wiadomości", SkillCapability.MESSAGE_CAPTURE)
        val updated = original.reviseMetadata("ConversationStyle", metadata)
        val active = updated.snapshot().entries.single { it.id == "ConversationStyle" }
        assertEquals(3, active.version)
        assertEquals("Przyjmij zgłoszenie", active.name)
        assertEquals("Tylko fikcyjne wiadomości", active.description)
        assertEquals(SkillCapability.MESSAGE_CAPTURE, active.capability)
        assertEquals(old, original.snapshot())
        assertEquals(SkillCapability.GENERAL,
            updated.bindVersion("ConversationStyle", 1).snapshot().entries
                .single { it.id == "ConversationStyle" }.capability)
        assertEquals(SkillCapability.GENERAL,
            updated.snapshot("natural").entries.single { it.id == "ConversationStyle" }.capability)
    }

    @Test fun renamedSkillCanBecomeContextualWithoutIdWhitelistAndNeverChangesAuthority() {
        val lib = ConversationSkillLibrary.defaults().copyActive("Flexible")
            .reviseMetadata("ConversationStyle",
                SkillMetadata("Odbieranie wiadomości", "Zbieraj treść w Lab",
                    SkillCapability.MESSAGE_CAPTURE))
        val snap = lib.snapshot()
        val route = ConversationSkillRouter.route(
            snap, SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Zwykły neutralny dialog")
        assertFalse(route.selectedIds.contains("ConversationStyle"))
        val turns = ConversationSkillTurnRouter(snap, SkillLoadingMode.CONTEXTUAL,
            ConversationDirection.UNSPECIFIED, "Neutralny dialog")
        val first = turns.observeUserTurn("Zapisz wiadomość")
        assertEquals(setOf("ConversationStyle", "MessageCapture"),
            first.activatedIds.toSet())
        val activated = turns.observeUserTurn("Zapisz wiadomość")
        assertTrue(activated.selectedIds.contains("ConversationStyle"))
        assertTrue(activated.activatedIds.isEmpty())
        val pivot = turns.observeUserTurn("Zmienmy temat: nowy wątek")
        assertFalse(pivot.selectedIds.contains("ConversationStyle"))
        assertFalse(pivot.snapshot.entries.any { it.id == "ConversationStyle" })
    }

    @Test fun metadataRevisionsPersistAndRefuseBlankFields() {
        var record: String? = null
        val repo = ConversationSkillLibraryRepository({ record }, { record = it })
        val updated = repo.load().copyActive("Stored")
            .reviseMetadata("Clarification",
                SkillMetadata("Doprecyzuj", "Wersja 2", SkillCapability.CLARIFICATION))
        repo.save(updated)
        assertEquals(updated.snapshot(), repo.load().snapshot())
        assertEquals(2, repo.load().snapshot().entries.single { it.id == "Clarification" }.version)
        assertTrue(runCatching {
            updated.reviseMetadata("Clarification",
                SkillMetadata(" ", "Opis", SkillCapability.GENERAL))
        }.isFailure)
        assertTrue(runCatching {
            updated.reviseMetadata("Clarification",
                SkillMetadata("Nazwa", " ", SkillCapability.GENERAL))
        }.isFailure)
    }
}
