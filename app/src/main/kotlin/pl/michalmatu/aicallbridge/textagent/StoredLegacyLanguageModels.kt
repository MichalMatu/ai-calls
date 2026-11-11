package pl.michalmatu.aicallbridge.textagent

import java.io.File
import java.nio.file.Files
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/** A snapshot of one actual app-owned file, including incomplete retired downloads. */
internal data class StoredLegacyLanguageModel(
    val model: LanguageModelEntry,
    val bytes: Long,
    val modified: Long,
)

/** The catalog identifies files; it never stands in for an inventory of installed models. */
internal class StoredLegacyLanguageModels(private val directory: File) {
    fun list(): List<StoredLegacyLanguageModel> = LanguageModelCatalog.entries
        .filter { it.id != DialogueModelId.GEMMA_4_E2B }
        .mapNotNull { snapshot(it) }

    /** Caller confirms IDLE and reloads selection. Only the exact displayed snapshot is removed. */
    fun delete(confirmed: StoredLegacyLanguageModel, selected: DialogueModelId): Boolean {
        val model = LanguageModelCatalog.entries.firstOrNull { it.id == confirmed.model.id }
            ?: return false
        if (model.id == DialogueModelId.GEMMA_4_E2B || model.id == selected) return false
        if (snapshot(model) != confirmed) return false
        return File(directory, model.spec.fileName).delete()
    }

    private fun snapshot(model: LanguageModelEntry): StoredLegacyLanguageModel? {
        val file = File(directory, model.spec.fileName)
        if (!file.isFile || Files.isSymbolicLink(file.toPath())) return null
        return StoredLegacyLanguageModel(model, file.length(), file.lastModified())
    }
}
