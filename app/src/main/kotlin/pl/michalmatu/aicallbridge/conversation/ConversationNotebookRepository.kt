package pl.michalmatu.aicallbridge.conversation

import com.google.gson.Gson

/**
 * One encrypted index + one separately encrypted record per call. Read/write/delete callbacks
 * must use the existing no-backup Keystore/AtomicFile record layer.
 *
 * The legacy all-calls blob is imported before any new operation. Legacy is deleted only after
 * every call is durably indexed, so a crash cannot silently discard previously saved entries.
 */
internal class ConversationNotebookRepository(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
    private val remove: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Index(val schema: Int = 2, val sessionIds: List<String> = emptyList())
    private val gson = Gson()

    fun list(): List<ConversationNotebook> {
        migrateLegacy()
        return readIndex().sessionIds.mapNotNull { sessionId ->
            // Index registration may outlive an interrupted first append.
            readBook(sessionId)
        }.filter { it.entries.isNotEmpty() }
            .sortedByDescending { it.entries.lastOrNull()?.timestampMs ?: 0L }
    }

    fun append(sessionId: String, direction: String, turn: Int,
        proposal: pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal
    ): Boolean {
        require(ConversationNotebookPolicy.validSession(sessionId, direction, turn)) {
            "notebook_scope_invalid"
        }
        migrateLegacy()
        val index = readIndex()
        val current = readBook(sessionId)
            ?: ConversationNotebook(sessionId, direction, emptyList())
        val previous = ConversationNotebookLibrary(conversations = listOf(current))
        val next = ConversationNotebookPolicy.append(previous, sessionId, direction,
            turn, proposal, now())
        // Index first: a crash before the session write cannot orphan a successfully
        // written private note. A missing new record is a harmless incomplete attempt.
        if (sessionId !in index.sessionIds) {
            writeIndex(index.copy(sessionIds = index.sessionIds + sessionId))
        }
        if (next != previous) writeBook(next.conversations.single())
        return true
    }

    /** User-confirmed deletion. First overwrite with an empty encrypted record, then deindex. */
    fun delete(sessionId: String): Boolean {
        require(ConversationNotebookPolicy.validSession(sessionId, "INCOMING", 1)) {
            "notebook_scope_invalid"
        }
        migrateLegacy()
        val index = readIndex()
        if (sessionId !in index.sessionIds) return false
        val existing = requireNotNull(readBook(sessionId)) { "notebook_index_missing_record" }
        writeBook(existing.copy(entries = emptyList()))
        writeIndex(index.copy(sessionIds = index.sessionIds.filterNot { it == sessionId }))
        remove(sessionRecordId(sessionId))
        return true
    }

    private fun migrateLegacy() {
        val raw = read(LEGACY_ID) ?: return
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "legacy_notebook_storage_full"
        }
        val library = ConversationNotebookPolicy.validate(
            requireNotNull(gson.fromJson(raw, ConversationNotebookLibrary::class.java)) {
                "legacy_notebook_unreadable"
            })
        var index = readIndex()
        for (book in library.conversations) {
            val current = readBook(book.sessionId)
            if (current == null) writeBook(book)
            else require(current.direction == book.direction &&
                current.entries.containsAll(book.entries)) {
                "notebook_migration_conflict"
            }
            if (book.sessionId !in index.sessionIds) {
                index = index.copy(sessionIds = index.sessionIds + book.sessionId)
                writeIndex(index)
            }
        }
        // Retain the original on any failure above; remove only once every record is safe.
        remove(LEGACY_ID)
        require(read(LEGACY_ID) == null) { "legacy_notebook_migration_incomplete" }
    }

    private fun readIndex(): Index {
        val raw = read(INDEX_ID) ?: return Index()
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "notebook_index_too_large" }
        val value = requireNotNull(gson.fromJson(raw, Index::class.java)) {
            "notebook_index_unreadable"
        }
        require(value.schema == 2 && value.sessionIds.size == value.sessionIds.distinct().size &&
            value.sessionIds.all {
                ConversationNotebookPolicy.validSession(it, "INCOMING", 1)
            }) { "notebook_index_invalid" }
        return value
    }

    private fun writeIndex(index: Index) {
        val encoded = gson.toJson(index)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "notebook_index_too_large"
        }
        write(INDEX_ID, encoded)
    }

    private fun readBook(sessionId: String): ConversationNotebook? {
        val raw = read(sessionRecordId(sessionId)) ?: return null
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "notebook_record_too_large" }
        val book = requireNotNull(gson.fromJson(raw, ConversationNotebook::class.java)) {
            "notebook_record_unreadable"
        }
        require(book.sessionId == sessionId) { "notebook_record_scope_mismatch" }
        ConversationNotebookPolicy.validate(ConversationNotebookLibrary(
            conversations = listOf(book)))
        return book
    }

    private fun writeBook(book: ConversationNotebook) {
        ConversationNotebookPolicy.validate(ConversationNotebookLibrary(
            conversations = listOf(book)))
        val encoded = gson.toJson(book)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "notebook_record_too_large"
        }
        write(sessionRecordId(book.sessionId), encoded)
    }

    private fun sessionRecordId(sessionId: String): String = "notebook-call-v2-" + sessionId

    private companion object {
        const val LEGACY_ID = "notebook-library-v1"
        const val INDEX_ID = "notebook-index-v2"
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
