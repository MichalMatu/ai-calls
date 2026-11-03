package pl.michalmatu.aicallbridge.localspeech

/**
 * Splits authorized text for the platform's per-utterance TTS input capacity.
 * Unlike take(max), this returns ALL text in order, with no synthetic dialogue cutoff.
 */
internal object LocalTtsTextChunker {
    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars >= 2) { "tts_utterance_capacity_invalid" }
        require(text.isNotBlank()) { "tts_text_blank" }
        val chunks = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                // Do not cut a UTF-16 surrogate pair at a platform boundary.
                if (end > start && Character.isHighSurrogate(text[end - 1]) &&
                    Character.isLowSurrogate(text[end])
                ) end -= 1
                val halfway = start + (end - start) / 2
                val boundary = (end downTo halfway + 1).firstOrNull { index ->
                    val previous = text[index - 1]
                    previous.isWhitespace() || previous in ".!?;:,"
                }
                if (boundary != null) end = boundary
            }
            if (end <= start) end = minOf(start + maxChars, text.length)
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }
}
