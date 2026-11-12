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
            sameWords(joined, current) || sameWords(parts.last(), current) -> Unit
            beginsWithWords(current, joined) -> {
                parts.clear()
                parts += current
            }
            else -> parts += current
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

        val previous = accumulated.split(Regex("\\s+"))
        val tail = direct.split(Regex("\\s+"))
        val maximum = minOf(previous.size, tail.size)
        val overlap = (maximum downTo 1).firstOrNull { count ->
            previous.takeLast(count).map(::word) == tail.take(count).map(::word)
        } ?: 0
        return (previous + tail.drop(overlap)).joinToString(" ")
    }

    fun clear() = parts.clear()

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
