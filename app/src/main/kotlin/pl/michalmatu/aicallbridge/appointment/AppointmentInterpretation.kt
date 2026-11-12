package pl.michalmatu.aicallbridge.appointment

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.localcall.PhraseMatrix
import pl.michalmatu.aicallbridge.localcall.PhraseMatrixRule

enum class AppointmentDateKind {
    ABSOLUTE,
    RELATIVE,
    WEEKDAY,
}

data class AppointmentDateCandidate(
    val date: LocalDate,
    val kind: AppointmentDateKind,
)

data class AppointmentTimeCandidate(val time: LocalTime)

data class AppointmentTimeRangeCandidate(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "appointment_time_range_must_be_increasing" }
    }
}

data class AppointmentOfferCandidate(
    val scheduledAt: ZonedDateTime,
    val dateKind: AppointmentDateKind,
)

enum class AppointmentResponseIntent {
    ACCEPT,
    REJECT,
    REQUEST_ALTERNATIVE,
}

data class AppointmentIdentityFieldRequestCandidate(val fieldId: IdentityFieldId)

/**
 * Pure, deterministic BOOK_APPOINTMENT interpretation helpers.
 *
 * Every returned value is candidate data only. This class owns no TaskGraph reducer, workflow,
 * confirmation, commitment, output, telephony or IdentityVault API. Relative dates are resolved
 * only against the caller-supplied reference date; there is deliberately no hidden clock.
 */
class AppointmentInterpreter(private val zone: ZoneId) {
    fun parseDate(
        text: String,
        referenceDate: LocalDate? = null,
    ): AppointmentDateCandidate? {
        // A caller can mention alternatives or correct a date mid-sentence.
        // Never pick the first explicit date and invent a confirmed offer.
        val isoMatches = ISO_DATE.findAll(text).toList()
        val polishMatches = POLISH_DATE.findAll(text).toList()
        if (isoMatches.size + polishMatches.size > 1) return null

        val explicit = isoMatches.singleOrNull()?.let { match ->
            localDateOrNull(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
            )
        } ?: polishMatches.singleOrNull()?.let { match ->
            localDateOrNull(
                year = match.groupValues[3].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[1].toInt(),
            )
        }
        // An invalid explicit date cannot fall back to a different, implied date.
        if (isoMatches.isNotEmpty() || polishMatches.isNotEmpty()) {
            if (explicit == null) return null
        }

        val normalized = normalizeWords(text)
        val mentionsTomorrow = normalized.hasToken("jutro")
        val mentionsDayAfter = normalized.hasToken("pojutrze")
        val mentionsToday = normalized.hasToken("dzis") || normalized.hasToken("dzisiaj")
        val mentionedWeekdays = WEEKDAY_ALIASES.entries.filter { (_, aliases) ->
            aliases.any { alias -> normalized.hasToken(alias) }
        }.map { it.key }

        val anchor = referenceDate
        if (anchor == null) {
            // With no reference date we cannot check relative terms against
            // an explicit offer, so do not accept a contradictory mixed phrase.
            if (mentionsTomorrow || mentionsDayAfter || mentionsToday ||
                mentionedWeekdays.isNotEmpty()) return null
            return explicit?.let { AppointmentDateCandidate(it, AppointmentDateKind.ABSOLUTE) }
        }

        val relativeDates = buildList {
            if (mentionsDayAfter) add(anchor.plusDays(2))
            if (mentionsTomorrow) add(anchor.plusDays(1))
            if (mentionsToday) add(anchor)
        }
        val weekdayDates = mentionedWeekdays.map {
            anchor.with(TemporalAdjusters.nextOrSame(it))
        }
        val uniqueDates = (relativeDates + weekdayDates + listOfNotNull(explicit)).distinct()
        // "Jutro, w środę" is fine when both resolve to the same day;
        // "jutro, 2026-09-24" must not silently prefer one date.
        if (uniqueDates.size != 1) return null
        return AppointmentDateCandidate(
            date = uniqueDates.single(),
            kind = when {
                explicit != null -> AppointmentDateKind.ABSOLUTE
                relativeDates.isNotEmpty() -> AppointmentDateKind.RELATIVE
                else -> AppointmentDateKind.WEEKDAY
            },
        )
    }

    fun parseTime(text: String): AppointmentTimeCandidate? {
        // A date such as "10.02.2027" must not become either a clock time
        // (10:02) or a bare "o 10" after text normalization.
        val withoutDates = listOf(ISO_DATE, POLISH_DATE).fold(text) { remaining, date ->
            date.replace(remaining, " ")
        }
        val clockTimes = TIME.findAll(withoutDates).mapNotNull { match ->
            localTimeOrNull(match.groupValues[1], match.groupValues[2])
        }
        val markedHours = HOUR_WITH_MARKER.findAll(
            normalizeWordsKeepingTimeSeparators(withoutDates),
        ).mapNotNull { match ->
            val minute = match.groupValues[2].ifEmpty { "0" }
            localTimeOrNull(match.groupValues[1], minute)
        }
        // Check *all* clock evidence. A parsed "17:30" cannot silently
        // override a conflicting "o 18" later in the same utterance.
        return (clockTimes + markedHours).distinct().toList()
            .singleOrNull()?.let(::AppointmentTimeCandidate)
    }

    fun parseTimeRange(text: String): AppointmentTimeRangeCandidate? {
        val normalized = normalizeWordsKeepingTimeSeparators(text)
        val match = TIME_RANGE.find(normalized) ?: BETWEEN_TIME_RANGE.find(normalized)
            ?: return null
        val start = localTimeOrNull(
            match.groupValues[1],
            match.groupValues[2].ifEmpty { "0" },
        ) ?: return null
        val end = localTimeOrNull(
            match.groupValues[3],
            match.groupValues[4].ifEmpty { "0" },
        ) ?: return null
        if (start >= end) return null
        return AppointmentTimeRangeCandidate(start, end)
    }

    fun parseOffer(
        text: String,
        referenceDate: LocalDate? = null,
    ): AppointmentOfferCandidate? {
        // A spoken refusal can contain a complete date and clock time. It
        // must never become an apparently available appointment for review.
        // Keep negative availability distinct from "nie ma problemu" or "brak opłat".
        if (UNAVAILABLE_OFFER.containsMatchIn(normalizeWords(text))) return null
        // A range is not a concrete offered appointment even when malformed,
        // reversed or equal-ended. parseTimeRange intentionally returns null
        // for invalid ranges, so it cannot be the only guard.
        val normalized = normalizeWordsKeepingTimeSeparators(text)
        if (TIME_RANGE.containsMatchIn(normalized) ||
            BETWEEN_TIME_RANGE.containsMatchIn(normalized)) return null
        val date = parseDate(text, referenceDate) ?: return null
        val time = parseTime(text) ?: return null
        // Zone resolution can silently shift a nonexistent clock time (DST gap)
        // or choose an arbitrary offset when a local hour occurs twice. Neither
        // is a unique appointment offered by the other party.
        val local = LocalDateTime.of(date.date, time.time)
        val validOffsets = zone.rules.getValidOffsets(local)
        if (validOffsets.size != 1) return null
        return AppointmentOfferCandidate(
            ZonedDateTime.ofLocal(local, zone, validOffsets.single()),
            date.kind,
        )
    }

    fun classifyResponse(text: String): AppointmentResponseIntent? =
        when (AppointmentDialogueActPhraseMatrix.create().match(text)?.ruleId) {
            AppointmentDialogueActPhraseMatrix.ACCEPT_RULE_ID -> AppointmentResponseIntent.ACCEPT
            AppointmentDialogueActPhraseMatrix.REJECT_RULE_ID -> AppointmentResponseIntent.REJECT
            AppointmentDialogueActPhraseMatrix.REQUEST_ALTERNATIVE_RULE_ID ->
                AppointmentResponseIntent.REQUEST_ALTERNATIVE
            else -> null
        }

    fun parseIdentityFieldRequest(text: String): AppointmentIdentityFieldRequestCandidate? {
        val normalized = normalizeWords(text)
        val field = when {
            normalized.hasSequence("e mail") || normalized.hasToken("email") -> IdentityFieldId.EMAIL
            normalized.hasSequence("data urodzenia") || normalized.hasSequence("date urodzenia") ->
                IdentityFieldId.DATE_OF_BIRTH
            normalized.hasToken("pesel") -> IdentityFieldId.PESEL
            normalized.hasSequence("numer telefonu") || normalized.hasToken("telefon") -> IdentityFieldId.PHONE
            normalized.hasToken("imie") -> IdentityFieldId.FIRST_NAME
            normalized.hasToken("nazwisko") -> IdentityFieldId.LAST_NAME
            normalized.hasSequence("adres zamieszkania") || normalized.hasToken("adres") -> IdentityFieldId.ADDRESS
            else -> null
        } ?: return null
        return AppointmentIdentityFieldRequestCandidate(field)
    }

    private fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun localTimeOrNull(hour: String, minute: String): LocalTime? =
        runCatching { LocalTime.of(hour.toInt(), minute.toInt()) }.getOrNull()

    private companion object {
        val ISO_DATE = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
        val POLISH_DATE = Regex("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(?!\\d)")
        val TIME = Regex("(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)")
        val HOUR_WITH_MARKER = Regex("\\b(?:o|godzina|godz)\\.?\\s+([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\b")
        val TIME_RANGE = Regex(
            "\\bod\\s+([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s+do\\s+" +
                "([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\b",
        )
        val BETWEEN_TIME_RANGE = Regex(
            "\\bmiedzy\\s+([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s+a\\s+" +
                "([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\b",
        )
        val UNAVAILABLE_OFFER = Regex(
            "\\b(?:nie ma|nie mamy|brak)\\s+(?:woln\\w*|dostepn\\w*|termin\\w*|miejsc\\w*)\\b" +
                "|\\b(?:zajety|zajeta|zajete|niedostepny|niedostepna|niedostepne|odpada)\\b",
        )
        val WEEKDAY_ALIASES = mapOf(
            DayOfWeek.MONDAY to setOf("poniedzialek"),
            DayOfWeek.TUESDAY to setOf("wtorek"),
            DayOfWeek.WEDNESDAY to setOf("sroda", "srode"),
            DayOfWeek.THURSDAY to setOf("czwartek"),
            DayOfWeek.FRIDAY to setOf("piatek"),
            DayOfWeek.SATURDAY to setOf("sobota", "sobote"),
            DayOfWeek.SUNDAY to setOf("niedziela", "niedziele"),
        )

        fun normalizeWords(value: String): String = normalize(value, keepTimeSeparators = false)

        fun normalizeWordsKeepingTimeSeparators(value: String): String =
            normalize(value, keepTimeSeparators = true)

        fun normalize(value: String, keepTimeSeparators: Boolean): String {
            val folded = value.lowercase(Locale.ROOT)
                .replace('ą', 'a')
                .replace('ć', 'c')
                .replace('ę', 'e')
                .replace('ł', 'l')
                .replace('ń', 'n')
                .replace('ó', 'o')
                .replace('ś', 's')
                .replace('ź', 'z')
                .replace('ż', 'z')
            val normalized = buildString(folded.length) {
                folded.forEach { character ->
                    when {
                        character.isLetterOrDigit() -> append(character)
                        keepTimeSeparators && (character == ':' || character == '.') -> append(character)
                        else -> append(' ')
                    }
                }
            }
            return normalized.trim().replace(Regex("\\s+"), " ")
        }

        fun String.hasToken(token: String): Boolean = " $this ".contains(" $token ")
        fun String.hasSequence(sequence: String): Boolean = " $this ".contains(" $sequence ")
    }
}

/**
 * Appointment-only deterministic dialogue acts. Rule IDs are classification metadata only; they
 * carry no CallPlan, workflow, confirmation or commitment authority by themselves.
 */
object AppointmentDialogueActPhraseMatrix {
    const val ACCEPT_RULE_ID = "appointment.accept"
    const val REJECT_RULE_ID = "appointment.reject"
    const val REQUEST_ALTERNATIVE_RULE_ID = "appointment.request_alternative"

    fun create(): PhraseMatrix = PhraseMatrix(
        listOf(
            PhraseMatrixRule(
                ruleId = ACCEPT_RULE_ID,
                phrases = setOf("tak pasuje"),
                aliases = setOf("pasuje", "ten termin pasuje", "tak proszę"),
                variantClass = "appointment_response",
            ),
            PhraseMatrixRule(
                ruleId = REJECT_RULE_ID,
                phrases = setOf("nie dziękuję"),
                aliases = setOf("nie pasuje", "ten termin nie pasuje"),
                variantClass = "appointment_response",
            ),
            PhraseMatrixRule(
                ruleId = REQUEST_ALTERNATIVE_RULE_ID,
                phrases = setOf("czy jest inny termin"),
                aliases = setOf("poproszę inny termin", "proszę o inny termin", "inny termin proszę"),
                variantClass = "appointment_response",
            ),
        ),
    )
}
