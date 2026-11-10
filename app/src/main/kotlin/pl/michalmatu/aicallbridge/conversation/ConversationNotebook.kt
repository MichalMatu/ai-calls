package pl.michalmatu.aicallbridge.conversation

import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteKind
import pl.michalmatu.aicallbridge.conversationtools.ConversationNoteProposal

/** A local, encrypted, append-only record. Never sent to the model or external services. */
internal data class ConversationNotebookEntry(
    val id: String,
    val turn: Int,
    val timestampMs: Long,
    val kind: ConversationNoteKind,
    val text: String,
) {
    override fun toString(): String = "ConversationNotebookEntry(REDACTED)"
}
internal data class ConversationNotebook(
    val sessionId: String,
    val direction: String,
    val entries: List<ConversationNotebookEntry>,
) {
    override fun toString(): String = "ConversationNotebook(REDACTED)"
}
internal data class ConversationNotebookLibrary(
    val schema: Int = 1,
    val conversations: List<ConversationNotebook> = emptyList(),
) {
    override fun toString(): String = "ConversationNotebookLibrary(REDACTED)"
}

/** Model-independent call scope, uniqueness and type validation, no external effects. */
internal object ConversationNotebookPolicy {
    private val sessionIdRegex = Regex("[A-Za-z0-9_-]{1,128}")
    fun validSession(sessionId: String, direction: String, turn: Int): Boolean =
        sessionIdRegex.matches(sessionId) &&
            direction in setOf("INCOMING", "OUTGOING") && turn > 0

    fun append(
        library: ConversationNotebookLibrary,
        sessionId: String,
        direction: String,
        turn: Int,
        proposal: ConversationNoteProposal,
        timestampMs: Long,
    ): ConversationNotebookLibrary {
        require(library.schema == 1 && validSession(sessionId, direction, turn)) {
            "notebook_scope_invalid"
        }
        val found = library.conversations.singleOrNull { it.sessionId == sessionId }
        require(found == null || found.direction == direction) { "notebook_direction_mismatch" }
        val id = sessionId + ":" + turn
        found?.entries?.firstOrNull { it.id == id }?.let {
            require(it.kind == proposal.kind && it.text == proposal.content) {
                "notebook_idempotency_conflict"
            }
            return library
        }
        val entry = ConversationNotebookEntry(id, turn, timestampMs,
            proposal.kind, proposal.content)
        val updated = (found ?: ConversationNotebook(sessionId, direction, emptyList()))
            .copy(entries = found?.entries.orEmpty() + entry)
        return library.copy(conversations =
            library.conversations.filterNot { it.sessionId == sessionId } + updated)
    }

    fun validate(library: ConversationNotebookLibrary): ConversationNotebookLibrary {
        require(library.schema == 1 &&
            library.conversations.map { it.sessionId }.distinct().size ==
                library.conversations.size) { "notebook_library_invalid" }
        library.conversations.forEach { book ->
            require(validSession(book.sessionId, book.direction, 1)) {
                "notebook_book_invalid"
            }
            require(book.entries.map { it.id }.distinct().size == book.entries.size) {
                "notebook_entries_duplicate"
            }
            book.entries.forEach { e ->
                require(e.id == book.sessionId + ":" + e.turn && e.turn > 0 &&
                    e.timestampMs > 0L) { "notebook_entry_invalid" }
                ConversationNoteProposal(e.kind, e.text)
            }
        }
        return library
    }
}
