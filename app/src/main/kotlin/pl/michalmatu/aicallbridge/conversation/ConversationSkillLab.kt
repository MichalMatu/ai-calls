package pl.michalmatu.aicallbridge.conversation

/** Synthetic only: never telephone numbers, real transcripts, commitments or media. */
internal data class SkillLabTurn(
    val utterance: String,
    val shouldEnd: Boolean,
    /** null means no judgement; false/true is a question-mark heuristic, not semantic scoring. */
    val expectQuestion: Boolean? = null,
)
internal data class SkillLabStory(val title: String, val turns: List<SkillLabTurn>)
internal object SkillLabCorpus {
    val stories: List<SkillLabStory> = listOf(
        SkillLabStory("Cytat, korekta, podsumowanie, koniec", listOf(
            SkillLabTurn("Chcę zostawić wiadomość, że rower jest gotowy do odbioru.", false),
            SkillLabTurn("W wiadomości padło słowo do widzenia, ale mam poprawkę.", false),
            SkillLabTurn("Zamiast wtorku proszę zapisać czwartek po szesnastej.", false),
            SkillLabTurn("Proszę teraz podsumować poprawioną wiadomość.", false, false),
            SkillLabTurn("Tak, wszystko się zgadza. Dziękuję za pomoc, do widzenia.", true, false),
        )),
        SkillLabStory("Dodatkowa sprawa po podziękowaniu", listOf(
            SkillLabTurn("Zapisz proszę, że przesyłka jest do odebrania.", false),
            SkillLabTurn("Dziękuję, ale zanim skończymy, mam jeszcze drugą sprawę.", false),
            SkillLabTurn("Zapisz również prośbę o kontakt w piątek.", false),
            SkillLabTurn("To już wszystkie sprawy. Dziękuję i żegnam.", true, false),
        )),
        SkillLabStory("Wyraźna prośba o rozłączenie", listOf(
            SkillLabTurn("Nie zostawiam wiadomości. Proszę zakończyć teraz połączenie.", true, false),
        )),
        SkillLabStory("Niejasność i dopiero potem koniec", listOf(
            SkillLabTurn("Chcę przekazać ważną wiadomość, tylko jeszcze nie wiem, o której godzinie.", false, true),
            SkillLabTurn("O godzinie 17. Dziękuję, to wszystko, do widzenia.", true, false),
        )),
    )
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
            "; zbędne pytania (heurystyka '?'): " + unexpectedQuestions +
            "; średni czas odpowiedzi: " + meanLatencyMs + " ms.\n" + naturalness
    }
}
internal object SkillLabScoring {
    fun score(entries: List<SkillLabObservation>): SkillLabMetrics {
        return SkillLabMetrics(
            turns = entries.size,
            accurateEndDecisions = entries.count { it.expectedEnd == it.actualEnd },
            prematureEnds = entries.count { !it.expectedEnd && it.actualEnd },
            missingEnds = entries.count { it.expectedEnd && !it.actualEnd },
            unexpectedQuestions = entries.count {
                it.expectedQuestion == false && "?" in it.reply
            },
            meanLatencyMs = if (entries.isEmpty()) 0L else entries.sumOf { it.elapsedMs } / entries.size,
        )
    }
}
