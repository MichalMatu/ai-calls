package pl.michalmatu.aicallbridge.campaign

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.identity.IdentityFieldId

class AppointmentMvpTaskTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val from = ZonedDateTime.of(2027, 2, 10, 9, 0, 0, 0, zone)
    private val to = from.plusHours(3)
    private fun task(fields: Set<IdentityFieldId> = emptySet()) =
        AppointmentMvpTask("synthetic-grant", "+48000000000", 1,
            "voice-subscription:1:slot:0:carrier:0", "Umów wizytę u dentysty",
            from, to, "Bez dodatkowych opłat", fields)
    @Test fun exactTaskGrantMatchesOnlyOneNumberSimAndDisclosureSet() {
        val t = task(setOf(IdentityFieldId.FIRST_NAME))
        assertTrue(CampaignAuthorizationPolicy.matchesExactTask(t.grant(100L), t.request()))
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(
            t.grant(100L), t.copy(target = "+48111111111").request()))
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(
            t.grant(100L), t.copy(accountScope = "foreign-sim").request()))
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(
            t.grant(100L), t.copy(disclosureFields = setOf(IdentityFieldId.EMAIL)).request()))
        assertEquals("BOOK_APPOINTMENT", t.toCallTask().action())
    }
    @Test fun receptionistOfferRequiresExactTaskWindowAndOnlyReturnsReview() {
        val r = AppointmentMvpReviewReducer(task())
        val date = LocalDate.of(2027, 2, 10)
        assertNull(r.observe("Mamy 10.02.2027 o 08:00", date))
        assertNull(r.observe("Może jakoś po południu", date))
        val proposal = requireNotNull(r.observe("Mamy 10.02.2027 o 11:30", date))
        assertEquals(AppointmentMvpReview.REVIEW_REQUIRED, proposal.status)
        assertEquals(ZonedDateTime.of(2027, 2, 10, 11, 30, 0, 0, zone), proposal.at)
        assertFalse(proposal.toString().contains("11:30"))
        assertNull(r.observe("Mamy 10.02.2027 o 15:00", date))
    }
    @Test fun proposedAppointmentUsesApprovedTaskZoneRatherThanDeviceDefaultZone() {
        val newYork = ZoneId.of("America/New_York")
        val localStart = ZonedDateTime.of(2027, 2, 10, 9, 0, 0, 0, newYork)
        val localTask = task().copy(
            notBefore = localStart,
            notAfter = localStart.plusHours(3),
        )
        val reducer = AppointmentMvpReviewReducer(localTask)
        val offered = requireNotNull(reducer.observe(
            "Mamy 10.02.2027 o 11:30", LocalDate.of(2027, 2, 10),
        ))
        assertEquals(
            ZonedDateTime.of(2027, 2, 10, 11, 30, 0, 0, newYork),
            offered.at,
        )
        assertEquals(AppointmentMvpReview.REVIEW_REQUIRED, offered.status)
        assertNull(reducer.observe(
            "Mamy 10.02.2027 o 15:30", LocalDate.of(2027, 2, 10),
        ))
    }

    @Test fun highSensitivityAndBadTelephoneAreNotGrantable() {
        assertThrows(IllegalArgumentException::class.java) {
            task().copy(disclosureFields = setOf(IdentityFieldId.PESEL))
        }
        assertThrows(IllegalArgumentException::class.java) {
            task().copy(target = "*100")
        }
    }
}
