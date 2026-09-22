package pl.michalmatu.aicallbridge.appointment

import java.time.DayOfWeek
import java.time.LocalDate
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
        ISO_DATE.find(text)?.let { match ->
            return localDateOrNull(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
            )?.let { AppointmentDateCandidate(it, AppointmentDateKind.ABSOLUTE) }
        }

        POLISH_DATE.find(text)?.let { match ->
            return localDateOrNull(
                year = match.groupValues[3].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[1].toInt(),
            )?.let { AppointmentDateCandidate(it, AppointmentDateKind.ABSOLUTE) }
        }

        val anchor = referenceDate ?: return null
        val normalized = normalizeWords(text)
        when {
            normalized.hasToken("pojutrze") -> return AppointmentDateCandidate(
                anchor.plusDays(2),
                AppointmentDateKind.RELATIVE,
            )
            normalized.hasToken("jutro") -> return AppointmentDateCandidate(
                anchor.plusDays(1),
                AppointmentDateKind.RELATIVE,
            )
            normalized.hasToken("dzis") || normalized.hasToken("dzisiaj") ->
                return AppointmentDateCandidate(anchor, AppointmentDateKind.RELATIVE)
        }

        val weekday = WEEKDAY_ALIASES.entries.firstOrNull { (_, aliases) ->
            aliases.any { alias -> normalized.hasToken(alias) }
        }?.key ?: return null
        return AppointmentDateCandidate(
            date = anchor.with(TemporalAdjusters.nextOrSame(weekday)),
            kind = AppointmentDateKind.WEEKDAY,
        )
    }

    fun parseTime(text: String): AppointmentTimeCandidate? {
        TIME.find(text)?.let { match ->
            return localTimeOrNull(match.groupValues[1], match.groupValues[2])
                ?.let(::AppointmentTimeCandidate)
        }

        HOUR_WITH_MARKER.find(normalizeWords(text))?.let { match ->
            val minute = match.groupValues[2].ifEmpty { "0" }
            return localTimeOrNull(match.groupValues[1], minute)
                ?.let(::AppointmentTimeCandidate)
        }
        return null
    }

    fun parseTimeRange(text: String): AppointmentTimeRangeCandidate? {
        val match = TIME_RANGE.find(normalizeWordsKeepingTimeSeparators(text)) ?: return null
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
        // A range is not a concrete offered appointment and must not silently collapse to its start.
        if (parseTimeRange(text) != null) return null
        val date = parseDate(text, referenceDate) ?: return null
        val time = parseTime(text) ?: return null
        val scheduledAt = runCatching { ZonedDateTime.of(date.date, time.time, zone) }.getOrNull()
            ?: return null
        return AppointmentOfferCandidate(scheduledAt, date.kind)
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
            normalized.hasSequence("data urodzenia") -> IdentityFieldId.DATE_OF_BIRTH
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
        val HOUR_WITH_MARKER = Regex("\\b(?:o|godzina|godz)\\s+([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\b")
        val TIME_RANGE = Regex(
            "\\bod\\s+([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s+do\\s+" +
                "([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\b",
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
