package pl.michalmatu.aicallbridge.conversation

/** Synthetic only: never telephone numbers, real transcripts, commitments or media. */
internal data class SkillLabTurn(
    val utterance: String,
    val shouldEnd: Boolean,
    /** null means no judgement; false/true enables a conservative review heuristic, not semantic scoring. */
    val expectQuestion: Boolean? = null,
)
internal data class SkillLabStory(val title: String, val turns: List<SkillLabTurn>)
/** No shipped dialogue scripts. Editable and adaptive test cases are the next Lab milestone. */
internal object SkillLabCorpus {
    val stories: List<SkillLabStory> = emptyList()
}
internal data class SkillLabObservation(
    val story: String,
    val index: Int,
    val expectedEnd: Boolean,
    val actualEnd: Boolean,
    val reply: String,
    val elapsedMs: Long,
    val expectedQuestion: Boolean? = null,
)
internal data class SkillLabMetrics(
    val turns: Int,
    val accurateEndDecisions: Int,
    val prematureEnds: Int,
    val missingEnds: Int,
    val unexpectedQuestions: Int,
    val meanLatencyMs: Long,
) {
    fun describe(humanNaturalness: Int? = null): String {
        val naturalness = humanNaturalness?.let { "Ocena naturalności (ręczna): " + it + "/5." }
            ?: "Ocena naturalności: nieprzyznana (wymaga odsłuchu/lektury przez człowieka)."
        return "Decyzje END_CALL: " + accurateEndDecisions + "/" + turns +
            "; przedwczesne zakończenia: " + prematureEnds +
            "; brak zakończenia: " + missingEnds +
            "; pytania do przeglądu (heurystyka): " + unexpectedQuestions +
            "; średni czas odpowiedzi: " + meanLatencyMs + " ms.\n" + naturalness
    }
}
internal object SkillLabScoring {
    // Portable across Android/host Java regex implementations: no inline flag modes.
    // Only marks a candidate for human review, never a semantic correctness judgement.
    private val questionWords = setOf("czy", "kiedy", "gdzie", "jaki", "jaka",
        "jakie", "który", "która", "podaj", "podajcie")

    fun mayNeedQuestionReview(reply: String): Boolean {
        if ('?' in reply) return true
        val words = mutableListOf<String>()
        val token = StringBuilder()
        for (char in reply.lowercase(java.util.Locale.ROOT)) {
            if (char.isLetterOrDigit()) {
                token.append(char)
            } else if (token.isNotEmpty()) {
                words.add(token.toString())
                token.setLength(0)
            }
        }
        if (token.isNotEmpty()) words.add(token.toString())
        if (words.any { it in questionWords }) return true
        return words.windowed(2).any { pair ->
            (pair[0] == "proszę" && pair[1] in setOf("podać", "o")) ||
                (pair[0] == "potrzebuję" && pair[1] in setOf("numeru", "adresu"))
        }
    }

    fun score(entries: List<SkillLabObservation>): SkillLabMetrics {
        return SkillLabMetrics(
            turns = entries.size,
            accurateEndDecisions = entries.count { it.expectedEnd == it.actualEnd },
            prematureEnds = entries.count { !it.expectedEnd && it.actualEnd },
            missingEnds = entries.count { it.expectedEnd && !it.actualEnd },
            unexpectedQuestions = entries.count {
                it.expectedQuestion == false && mayNeedQuestionReview(it.reply)
            },
            meanLatencyMs = if (entries.isEmpty()) 0L else entries.sumOf { it.elapsedMs } / entries.size,
        )
    }
}
