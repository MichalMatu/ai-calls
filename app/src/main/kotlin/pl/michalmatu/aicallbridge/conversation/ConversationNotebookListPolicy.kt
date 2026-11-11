package pl.michalmatu.aicallbridge.conversation

import java.util.Locale

/** Pure projection for notebook search/filter/pagination, without Android or crypto access. */
internal object ConversationNotebookListPolicy {
    fun filter(books: List<ConversationNotebook>, query: String, direction: String?): List<ConversationNotebook> {
        val search = query.trim().lowercase(Locale.getDefault())
        return books.asSequence()
            .filter { direction == null || it.direction == direction }
            .filter { book -> search.isBlank() ||
                book.entries.any { entry ->
                    entry.text.lowercase(Locale.getDefault()).contains(search) ||
                        entry.kind.name.lowercase(Locale.ROOT).contains(search)
                }
            }
            .sortedByDescending { it.entries.lastOrNull()?.timestampMs ?: 0L }
            .toList()
    }
}
