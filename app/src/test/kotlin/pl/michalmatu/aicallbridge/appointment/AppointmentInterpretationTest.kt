package pl.michalmatu.aicallbridge.appointment

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.localcall.PhraseMatcherKind

class AppointmentInterpretationTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val referenceDate = LocalDate.of(2026, 9, 22)
    private val interpreter = AppointmentInterpreter(zone)

    @Test
    fun `absolute date and time formats preserve simulator coverage`() {
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone),
                AppointmentDateKind.ABSOLUTE,
            ),
            interpreter.parseOffer("2026-09-24 17:30", referenceDate),
        )
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone),
                AppointmentDateKind.ABSOLUTE,
            ),
            interpreter.parseOffer("wolny termin 24.09.2026, godzina 17:30", referenceDate),
        )
    }

    @Test
    fun `relative dates and weekdays are resolved only against explicit reference date`() {
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2026, 9, 23), AppointmentDateKind.RELATIVE),
            interpreter.parseDate("jutro", referenceDate),
        )
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2026, 9, 24), AppointmentDateKind.RELATIVE),
            interpreter.parseDate("pojutrze", referenceDate),
        )
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2026, 9, 24), AppointmentDateKind.WEEKDAY),
            interpreter.parseDate("w czwartek", referenceDate),
        )
        assertNull(interpreter.parseDate("kiedyś później", referenceDate))
    }

    @Test
    fun `times and time ranges normalize into typed candidates`() {
        assertEquals(AppointmentTimeCandidate(LocalTime.of(9, 5)), interpreter.parseTime("o 9:05"))
        assertEquals(
            AppointmentTimeRangeCandidate(LocalTime.of(16, 0), LocalTime.of(18, 30)),
            interpreter.parseTimeRange("od 16:00 do 18:30"),
        )
        assertNull(interpreter.parseTimeRange("od 18:30 do 16:00"))
        assertNull(interpreter.parseTime("po południu"))
    }

    @Test
    fun `relative offered appointment is a candidate and ambiguous offer fails closed`() {
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2026, 9, 23, 16, 30, 0, 0, zone),
                AppointmentDateKind.RELATIVE,
            ),
            interpreter.parseOffer("jutro o 16:30", referenceDate),
        )
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
                AppointmentDateKind.WEEKDAY,
            ),
            interpreter.parseOffer("w czwartek o 17:00", referenceDate),
        )
        assertNull(interpreter.parseOffer("jutro po południu", referenceDate))
    }

    @Test
    fun `response semantics classify only bounded deterministic acts`() {
        assertEquals(AppointmentResponseIntent.ACCEPT, interpreter.classifyResponse("tak, pasuje"))
        assertEquals(AppointmentResponseIntent.REJECT, interpreter.classifyResponse("nie, dziękuję"))
        assertEquals(
            AppointmentResponseIntent.REQUEST_ALTERNATIVE,
            interpreter.classifyResponse("czy jest inny termin?"),
        )
        assertNull(interpreter.classifyResponse("hmm, zobaczymy"))
    }

    @Test
    fun `common identity requests yield field ids only`() {
        assertEquals(
            AppointmentIdentityFieldRequestCandidate(IdentityFieldId.FIRST_NAME),
            interpreter.parseIdentityFieldRequest("proszę podać imię"),
        )
        assertEquals(
            AppointmentIdentityFieldRequestCandidate(IdentityFieldId.LAST_NAME),
            interpreter.parseIdentityFieldRequest("poproszę nazwisko"),
        )
        assertEquals(
            AppointmentIdentityFieldRequestCandidate(IdentityFieldId.PHONE),
            interpreter.parseIdentityFieldRequest("jaki jest numer telefonu?"),
        )
        assertEquals(
            AppointmentIdentityFieldRequestCandidate(IdentityFieldId.EMAIL),
            interpreter.parseIdentityFieldRequest("proszę adres e-mail"),
        )
        assertEquals(
            AppointmentIdentityFieldRequestCandidate(IdentityFieldId.DATE_OF_BIRTH),
            interpreter.parseIdentityFieldRequest("proszę podać datę urodzenia"),
        )
        assertEquals(
            AppointmentIdentityFieldRequestCandidate(IdentityFieldId.PESEL),
            interpreter.parseIdentityFieldRequest("proszę podać PESEL"),
        )
        assertNull(interpreter.parseIdentityFieldRequest("proszę potwierdzić termin"))
    }

    @Test
    fun `appointment dialogue acts expose deterministic PhraseMatrix rule ids only`() {
        val matrix = AppointmentDialogueActPhraseMatrix.create()

        val accept = requireNotNull(matrix.match("tak, pasuje"))
        assertEquals(AppointmentDialogueActPhraseMatrix.ACCEPT_RULE_ID, accept.ruleId)
        assertEquals(PhraseMatcherKind.EXACT, accept.matcherKind)

        val reject = requireNotNull(matrix.match("nie dziękuję"))
        assertEquals(AppointmentDialogueActPhraseMatrix.REJECT_RULE_ID, reject.ruleId)

        val alternative = requireNotNull(matrix.match("czy jest inny termin?"))
        assertEquals(AppointmentDialogueActPhraseMatrix.REQUEST_ALTERNATIVE_RULE_ID, alternative.ruleId)

        assertNull(matrix.match("jutro o 17:30"))
        assertNull(matrix.match("zarezerwuj to bez pytania"))
    }
}
