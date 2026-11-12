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
    fun `contradictory relative or weekday proposals require one clear date`() {
        assertNull(interpreter.parseDate("jutro albo pojutrze", referenceDate))
        assertNull(interpreter.parseDate("w środę lub w czwartek", referenceDate))
        assertNull(interpreter.parseOffer("jutro lub pojutrze o 17:00", referenceDate))
        assertNull(interpreter.parseDate("jutro w piątek", referenceDate))
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2026, 9, 23), AppointmentDateKind.RELATIVE),
            interpreter.parseDate("jutro, w środę", referenceDate),
        )
    }

    @Test
    fun `explicit and spoken relative dates must agree before accepting an offer`() {
        assertNull(interpreter.parseDate("jutro, 2026-09-24", referenceDate))
        assertNull(interpreter.parseOffer("jutro, 2026-09-24 o 17:00", referenceDate))
        assertNull(interpreter.parseDate("2026-09-23 w czwartek", referenceDate))
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2026, 9, 23), AppointmentDateKind.ABSOLUTE),
            interpreter.parseDate("jutro, w środę, 2026-09-23", referenceDate),
        )
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2026, 9, 23), AppointmentDateKind.ABSOLUTE),
            interpreter.parseDate("2026-09-23"),
        )
        // No clock anchor means the relative part is impossible to validate.
        assertNull(interpreter.parseDate("2026-09-23 jutro"))
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
    fun `explicit date without clock cannot fabricate an appointment hour`() {
        assertNull(interpreter.parseTime("10.02.2027"))
        assertNull(interpreter.parseTime("wizyta o 10.02.2027"))
        assertNull(interpreter.parseTime("wizyta dnia 2027-02-10"))
        assertNull(interpreter.parseOffer("wizyta o 10.02.2027", referenceDate))
        assertEquals(
            AppointmentTimeCandidate(LocalTime.of(17, 0)),
            interpreter.parseTime("10.02.2027, godz. 17"),
        )
    }

    @Test
    fun `conflicting clock and bare hour require clarification`() {
        assertNull(interpreter.parseTime("o 17:30, ewentualnie godz. 18"))
        assertNull(interpreter.parseOffer("10.02.2027 o 17:30 lub godz 18", referenceDate))
        assertEquals(
            AppointmentTimeCandidate(LocalTime.of(17, 30)),
            interpreter.parseTime("10.02.2027 o 17:30 i godz. 17:30"),
        )
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
    fun `nonexistent DST hour never silently shifts an offered Warsaw appointment`() {
        // Warsaw jumps from 02:00 to 03:00 on the last Sunday of March.
        assertNull(interpreter.parseOffer("2027-03-28 02:30", referenceDate))
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2027, 3, 28, 3, 30, 0, 0, zone),
                AppointmentDateKind.ABSOLUTE,
            ),
            interpreter.parseOffer("2027-03-28 03:30", referenceDate),
        )
    }

    @Test
    fun `ambiguous repeated DST hour requires clarification not arbitrary offset`() {
        // 02:30 is repeated after the Warsaw autumn clock rollback.
        assertNull(interpreter.parseOffer("31.10.2027 o 02:30", referenceDate))
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2027, 10, 31, 3, 30, 0, 0, zone),
                AppointmentDateKind.ABSOLUTE,
            ),
            interpreter.parseOffer("31.10.2027 o 03:30", referenceDate),
        )
    }

    @Test
    fun `conflicting or invalid explicit dates cannot silently become another offered day`() {
        assertNull(interpreter.parseDate("2027-02-10 lub 2027-02-11", referenceDate))
        assertNull(interpreter.parseDate("10.02.2027 albo 11.02.2027", referenceDate))
        assertNull(interpreter.parseDate("2027-02-31, jutro", referenceDate))
        assertNull(interpreter.parseOffer("10.02.2027 lub 11.02.2027 o 17:00", referenceDate))
        assertEquals(
            AppointmentDateCandidate(LocalDate.of(2027, 2, 10), AppointmentDateKind.ABSOLUTE),
            interpreter.parseDate("10.02.2027 o 17:00", referenceDate),
        )
    }

    @Test
    fun `competing clock times are not reduced to the first one`() {
        assertNull(interpreter.parseTime("o 17:00 albo 19:00"))
        assertNull(interpreter.parseTime("godz 17 lub godz 19"))
        assertNull(interpreter.parseOffer("10.02.2027 o 17:00 lub 18:00", referenceDate))
        assertEquals(
            AppointmentTimeCandidate(LocalTime.of(17, 30)),
            interpreter.parseTime("17:30, potwierdzam 17:30"),
        )
        assertEquals(
            AppointmentTimeCandidate(LocalTime.of(17, 0)),
            interpreter.parseTime("godz 17"),
        )
    }

    @Test
    fun `unavailable appointment cannot be reviewed as an offered appointment`() {
        assertNull(interpreter.parseOffer(
            "Niestety nie mamy wolnych terminów na 10.02.2027 o 11:30", referenceDate))
        assertNull(interpreter.parseOffer(
            "Termin 10.02.2027 o 11:30 jest już zajęty", referenceDate))
        assertNull(interpreter.parseOffer(
            "Brak miejsc 10.02.2027 o 11:30", referenceDate))
        assertNull(interpreter.parseOffer(
            "Termin 10.02.2027 o 11:30 odpada", referenceDate))
    }

    @Test
    fun `non availability negation still permits a genuinely offered time`() {
        assertEquals(
            AppointmentOfferCandidate(
                ZonedDateTime.of(2027, 2, 10, 11, 30, 0, 0, zone),
                AppointmentDateKind.ABSOLUTE,
            ),
            interpreter.parseOffer(
                "Nie ma problemu, wolny termin 10.02.2027 o 11:30, brak opłat", referenceDate),
        )
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
