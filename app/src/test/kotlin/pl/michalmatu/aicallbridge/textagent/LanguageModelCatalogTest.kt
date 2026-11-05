package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

class LanguageModelCatalogTest {
    @Test fun everySelectableModelHasItsOwnPinnedIdentityAndImmutablePublicSource() {
        val entries = LanguageModelCatalog.entries
        assertEquals(DialogueModelId.entries.toSet(), entries.map { it.id }.toSet())
        assertEquals(4, entries.map { it.spec.fileName }.toSet().size)
        assertEquals(4, entries.map { it.spec.sha256 }.toSet().size)
        for (entry in entries) {
            assertEquals(entry.spec.fileName, entry.source.fileName)
            assertEquals(entry.spec.sha256, entry.source.expectedSha256)
            assertEquals(entry.spec.expectedBytes, entry.source.expectedBytes)
            assertFalse(entry.source.requiresAuthentication)
            assertTrue(entry.source.downloadUrl.contains("/resolve/${entry.source.revision}/"))
        }
    }

    @Test fun existingE2BInstallationRemainsValidWithoutRedownload() {
        assertEquals(Gemma4ModelCatalog.GEMMA_4_E2B_IT, LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B].spec)
        assertEquals("gemma-4-E2B-it.litertlm", LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B].spec.fileName)
        assertEquals(2588147712L, LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B].spec.expectedBytes)
    }
}
