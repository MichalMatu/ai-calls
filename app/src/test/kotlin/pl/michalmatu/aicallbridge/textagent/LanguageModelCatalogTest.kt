package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

class LanguageModelCatalogTest {
    @Test fun everySelectableModelHasItsOwnPinnedIdentityAndImmutablePublicSource() {
        val entries = LanguageModelCatalog.entries
        assertEquals(DialogueModelId.entries.filterNot { it == DialogueModelId.USER_LITERT }.toSet(), entries.map { it.id }.toSet())
        assertEquals(entries.size, entries.map { it.spec.fileName }.toSet().size)
        assertEquals(entries.size, entries.map { it.spec.sha256 }.toSet().size)
        for (entry in entries) {
            assertEquals(entry.spec.fileName, entry.source.fileName)
            assertEquals(entry.spec.sha256, entry.source.expectedSha256)
            assertEquals(entry.spec.expectedBytes, entry.source.expectedBytes)
            assertFalse(entry.source.requiresAuthentication)
            assertTrue(entry.source.downloadUrl.contains("/resolve/${entry.source.revision}/"))
        }
    }

    @Test fun experimentalQwen35PinnedButNotProductionSelectable() {
        val candidate = LanguageModelCatalog[DialogueModelId.QWEN_3_5_2B]
        assertEquals("Qwen3.5-2B_int8.litertlm", candidate.spec.fileName)
        assertEquals("c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49", candidate.spec.sha256)
        assertEquals(2119843840L, candidate.spec.expectedBytes)
        assertEquals("f24013ba6844bd39be2d1f68e8d2421a77eed1f2", candidate.source.revision)
        assertEquals("qwen35_lab_only_unqualified",
            pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.incompatibility(
                pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile(
                    dialogue = DialogueModelId.QWEN_3_5_2B)))
        assertFalse(pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.dialogues.any {
            it.id == DialogueModelId.QWEN_3_5_2B.name
        })
        assertTrue(DialogueModelId.QWEN_3_5_2B.nativeExecutionPolicy().cpuOnly)
        assertTrue(DialogueModelId.QWEN_3_5_2B.nativeExecutionPolicy().newEngineAfterContextReset)
        assertFalse(DialogueModelId.GEMMA_4_E2B.nativeExecutionPolicy().cpuOnly)
    }

    @Test fun onlyBaseAndFileModelsAreVisibleInEngineSelection() {
        assertEquals(setOf(DialogueModelId.GEMMA_4_E2B.name, DialogueModelId.USER_LITERT.name),
            pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry.dialogues.map { it.id }.toSet())
    }

    @Test fun existingE2BInstallationRemainsValidWithoutRedownload() {
        assertEquals(Gemma4ModelCatalog.GEMMA_4_E2B_IT, LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B].spec)
        assertEquals("gemma-4-E2B-it.litertlm", LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B].spec.fileName)
        assertEquals(2588147712L, LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B].spec.expectedBytes)
    }
}
