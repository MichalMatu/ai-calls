package pl.michalmatu.aicallbridge.localspeech

/**
 * The Android recognizer may report completed segments followed by onResults containing
 * only the last fragment. Preserve the earlier utterance without duplicating an overlapping
 * final result. A complete onResults transcript still takes precedence.
 */
internal class SpeechRecognitionSegments {
    private val parts = mutableListOf<String>()

    fun add(segment: String) {
        val current = segment.trim()
        if (current.isBlank()) return
        val joined = parts.joinToString(" ")
        when {
            joined.isBlank() -> parts += current
            sameWords(joined, current) || sameWords(parts.last(), current) ||
                beginsWithWords(joined, current) || endsWithWords(joined, current) -> Unit
            beginsWithWords(current, joined) -> {
                parts.clear()
                parts += current
            }
            else -> {
                val previous = joined.split(Regex("\\s+"))
                val next = current.split(Regex("\\s+"))
                val overlap = overlapWords(previous, next)
                if (overlap == 0) parts += current
                else {
                    parts.clear()
                    parts += (previous + next.drop(overlap)).joinToString(" ")
                }
            }
        }
    }

    fun resolve(finalResult: String): String {
        val accumulated = parts.joinToString(" ")
        val direct = finalResult.trim()
        if (direct.isBlank()) return accumulated
        if (accumulated.isBlank()) return direct

        // The recognizer may send the full corrected utterance in onResults.
        if (beginsWithWords(direct, accumulated)) return direct
        if (beginsWithWords(accumulated, direct) || endsWithWords(accumulated, direct)) {
            return accumulated
        }
        // onResults may revise interior words in a whole-utterance hypothesis.
        // A shared greeting alone is not evidence of a complete replacement:
        // the recognizer might otherwise drop a confirmed earlier appointment.
        // Require agreement at both ends before replacing finalized segments.
        val completedWords = words(accumulated)
        val directWords = words(direct)
        if (completedWords.size >= 4 && directWords.size >= 4 &&
            completedWords.take(2) == directWords.take(2) &&
            completedWords.takeLast(2) == directWords.takeLast(2)) {
            // A dramatically shorter onResults value can be a clipped summary,
            // not a corrected full utterance. Preserve finalized appointment
            // conditions rather than silently dropping their middle words.
            return if (directWords.size >= completedWords.size - 1) direct else accumulated
        }

        val previous = accumulated.split(Regex("\\s+"))
        val tail = direct.split(Regex("\\s+"))
        return (previous + tail.drop(overlapWords(previous, tail))).joinToString(" ")
    }

    fun clear() = parts.clear()

    private fun overlapWords(previous: List<String>, next: List<String>): Int =
        (minOf(previous.size, next.size) downTo 1).firstOrNull { count ->
            previous.takeLast(count).map(::word) == next.take(count).map(::word)
        } ?: 0

    private fun sameWords(a: String, b: String): Boolean =
        words(a) == words(b)

    private fun beginsWithWords(text: String, beginning: String): Boolean =
        words(text).startsWith(words(beginning))

    private fun endsWithWords(text: String, ending: String): Boolean =
        words(text).endsWith(words(ending))

    private fun words(text: String): List<String> =
        text.trim().split(Regex("\\s+")).map(::word)

    private fun word(raw: String): String = raw.lowercase()
        .trim('.', ',', ';', ':', '!', '?', '„', '”', '"')

    private fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
        size >= prefix.size && take(prefix.size) == prefix

    private fun <T> List<T>.endsWith(suffix: List<T>): Boolean =
        size >= suffix.size && takeLast(suffix.size) == suffix
}
