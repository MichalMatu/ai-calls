package pl.michalmatu.aicallbridge.conversation

import java.util.Locale

/** Pure projection for notebook search/filter/order, without Android or crypto access. */
internal object ConversationNotebookListPolicy {
    fun filter(books: List<ConversationNotebook>, query: String, direction: String?): List<ConversationNotebook> {
        val search = query.trim().lowercase(Locale.ROOT)
        return books.asSequence()
            .filter { direction == null || it.direction == direction }
            .filter { book -> search.isBlank() ||
                book.entries.any { entry ->
                    entry.text.lowercase(Locale.ROOT).contains(search) ||
                        entry.kind.name.lowercase(Locale.ROOT).contains(search)
                }
            }
            .sortedByDescending { it.entries.lastOrNull()?.timestampMs ?: 0L }
            .toList()
    }
}
