package pl.michalmatu.aicallbridge.textagent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

class StoredLegacyLanguageModelsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun file(id: DialogueModelId) = File(temporary.root, LanguageModelCatalog[id].spec.fileName)
    private fun library() = StoredLegacyLanguageModels(temporary.root)

    @Test fun catalogAndProtectedOrUnrelatedFilesDoNotInventInstalledModels() {
        assertTrue(library().list().isEmpty())
        file(DialogueModelId.GEMMA_4_E2B).writeText("protected baseline")
        File(temporary.root, "user-file.litertlm").writeText("owner import")
        File(temporary.root, ".old.downloading").writeText("unfinished transfer")
        assertTrue(library().list().isEmpty())
    }

    @Test fun inventoryReportsActualSizesIncludingIncompleteRetiredFiles() {
        file(DialogueModelId.GEMMA_4_E4B).writeBytes(ByteArray(37))
        file(DialogueModelId.QWEN_3_1_7B).writeBytes(ByteArray(81))
        val inventory = library().list()
        assertEquals(setOf(DialogueModelId.GEMMA_4_E4B, DialogueModelId.QWEN_3_1_7B),
            inventory.map { it.model.id }.toSet())
        assertEquals(listOf(37L, 81L), inventory.map { it.bytes })
    }

    @Test fun removesOnlyConfirmedFileWithoutRequiringAnInstalledBaseline() {
        val chosen = file(DialogueModelId.GEMMA_4_E4B).apply { writeText("retired") }
        val other = file(DialogueModelId.QWEN_3_0_6B).apply { writeText("keep this") }
        val snapshot = library().list().single { it.model.id == DialogueModelId.GEMMA_4_E4B }
        assertTrue(library().delete(snapshot, DialogueModelId.GEMMA_4_E2B))
        assertFalse(chosen.exists())
        assertEquals("keep this", other.readText())
        assertFalse(file(DialogueModelId.GEMMA_4_E2B).exists())
    }

    @Test fun selectedAndProtectedBaselineFilesCannotBeDeletedEvenWithForgedSnapshot() {
        val base = file(DialogueModelId.GEMMA_4_E2B).apply { writeText("baseline") }
        file(DialogueModelId.GEMMA_4_E4B).writeText("selected")
        val selected = library().list().single()
        assertFalse(library().delete(selected, selected.model.id))
        assertFalse(library().delete(StoredLegacyLanguageModel(
            LanguageModelCatalog[DialogueModelId.GEMMA_4_E2B], base.length(), base.lastModified()),
            DialogueModelId.USER_LITERT))
        assertTrue(base.exists())
        assertTrue(file(DialogueModelId.GEMMA_4_E4B).exists())
    }

    @Test fun changedOrMissingFilesInvalidateTheOpenDeletionDialog() {
        val target = file(DialogueModelId.QWEN_3_0_6B).apply { writeText("original") }
        val snapshot = library().list().single()
        target.appendText(" replacement")
        assertFalse(library().delete(snapshot, DialogueModelId.GEMMA_4_E2B))
        assertTrue(target.exists())
        assertTrue(target.delete())
        assertFalse(library().delete(snapshot, DialogueModelId.GEMMA_4_E2B))
    }

    @Test fun directoriesAndSymbolicLinksNeverBecomeDeletionCandidates() {
        assertTrue(file(DialogueModelId.GEMMA_4_E4B).mkdir())
        val outside = temporary.newFile("valuable-file").apply { writeText("preserved") }
        Files.createSymbolicLink(file(DialogueModelId.QWEN_3_0_6B).toPath(), outside.toPath())
        assertTrue(library().list().isEmpty())
        assertEquals("preserved", outside.readText())
    }
}
