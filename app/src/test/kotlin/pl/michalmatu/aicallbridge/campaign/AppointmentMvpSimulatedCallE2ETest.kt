package pl.michalmatu.aicallbridge.campaign

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.*
import pl.michalmatu.aicallbridge.identity.*
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph

/** Entire phone exchange below is synthetic: no Android Telecom, network, PII or booking provider. */
class AppointmentMvpSimulatedCallE2ETest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val from = ZonedDateTime.of(2027, 2, 10, 9, 0, 0, 0, zone)
    private fun task() = AppointmentMvpTask(
        "synthetic-e2e-grant", "+48000000000", 1,
        "voice-subscription:1:slot:0:carrier:0",
        "Umów wizytę kontrolną", from, from.plusHours(8),
        "Nie zgadzam się na płatne usługi", setOf(IdentityFieldId.FIRST_NAME),
    )
    private class FakeCellularCall(val target: String, val scope: String) {
        var cellularConnected = false
        var aiOwnsAudio = false
        var disconnected = false
        val played = mutableListOf<String>()
        fun connect() { cellularConnected = true; aiOwnsAudio = true }
        fun speak(text: String) { if (aiOwnsAudio && cellularConnected) played += text }
        fun humanTakeover() { aiOwnsAudio = false }
        fun disconnect() { disconnected = true; cellularConnected = false; aiOwnsAudio = false }
    }

    @Test fun singleAuthorizedSimCallSttOfferReviewAndStopNeverBooks() {
        val task = task()
        val grant = task.grant(100L)
        assertTrue(CampaignAuthorizationPolicy.matchesExactTask(grant, task.request()))
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(grant,
            task.copy(target = "+48111111111").request()))
        assertFalse(CampaignAuthorizationPolicy.matchesExactTask(grant,
            task.copy(accountScope = "voice-subscription:2:slot:1:carrier:0").request()))

        val workflow = CallWorkflow(task.toCallTask(), CallConfirmationPolicy()) { }
        workflow.resolveTarget(CallResolvedTarget(task.goal, task.target))
        val fake = FakeCellularCall(task.target, task.accountScope)
        var disconnectCount = 0
        val owned = CampaignOwnedCallLifecycle<FakeCellularCall> {
            disconnectCount++
            it.disconnect()
            "exact_call_ended"
        }
        assertTrue(owned.bind(fake))
        workflow.markDialing()
        fake.connect()
        workflow.markCallActive()
        fake.speak("Dzień dobry, dzwonię w sprawie umówienia wizyty.")
        assertEquals(1, fake.played.size)

        // Incoming synthetic STT contains a concrete appointment. Neither STT nor
        // the fake model is a permission to book, approve a price, or disclose PII.
        val stt = "Proponujemy wizytę 10.02.2027 o 11:30."
        val review = requireNotNull(AppointmentMvpReviewReducer(task)
            .observe(stt, LocalDate.of(2027, 2, 10)))
        assertEquals(AppointmentMvpReview.REVIEW_REQUIRED, review.status)
        assertEquals(from.plusHours(2).plusMinutes(30), review.at)
        assertTrue(review.observedTerms.contains("11:30"))
        assertEquals(null, workflow.snapshot().outcome())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())

        val snapshot = AuthorizedFactSnapshot(task.toCallTask(),
            CallResolvedTarget(task.goal, task.target), 1L,
            setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.PHONE),
            task.disclosureFields, emptySet(),
            mapOf(IdentityFieldId.FIRST_NAME to setOf(BookAppointmentTaskGraph.WAITING_OFFER)))
        val allowed = FactDisclosureRequest(task.toCallTask(),
            CallResolvedTarget(task.goal, task.target), BookAppointmentTaskGraph.WAITING_OFFER,
            IdentityFieldId.FIRST_NAME, CallTaskMode.TEST, 1L)
        assertEquals(FactDisclosureDecision.ALLOW,
            DefaultFactDisclosurePolicy().decide(allowed, snapshot))
        assertEquals(FactDisclosureDecision.ASK_USER,
            DefaultFactDisclosurePolicy().decide(
                allowed.copy(fieldId = IdentityFieldId.PHONE), snapshot))

        fake.speak("Propozycja wymaga weryfikacji użytkownika; nie potwierdzam.")
        assertEquals("exact_call_ended", owned.releaseCurrent()) // operator STOP
        assertTrue(fake.disconnected)
        assertFalse(fake.aiOwnsAudio)
        assertEquals(1, disconnectCount)
        assertEquals("no_owned_call", owned.releaseCurrent())
        assertEquals(1, disconnectCount)
        assertEquals(null, workflow.snapshot().outcome())
    }

    @Test fun takeoverRevokesAiButPreservesTheExactHumanCall() {
        val task = task()
        val call = FakeCellularCall(task.target, task.accountScope)
        val another = FakeCellularCall("+48999999999", "foreign-sim")
        val disconnected = mutableListOf<FakeCellularCall>()
        val owned = CampaignOwnedCallLifecycle<FakeCellularCall> {
            disconnected += it
            it.disconnect()
            "exact_disconnected"
        }
        assertTrue(owned.bind(call))
        call.connect()
        another.connect()
        call.speak("O co mogę zapytać?")
        assertEquals(null, owned.detachIf { it === another })
        assertSame(call, owned.detachIf { it === call })
        call.humanTakeover()
        call.speak("SPÓŹNIONA ODPOWIEDŹ AI")
        assertFalse(call.disconnected)
        assertTrue(call.cellularConnected)
        assertFalse(call.aiOwnsAudio)
        assertEquals(1, call.played.size)
        assertEquals("no_owned_call", owned.releaseCurrent())
        assertTrue(disconnected.isEmpty())
        assertTrue(another.cellularConnected)
        assertFalse(another.disconnected)
    }

    @Test fun malformedOrUncertainOfferNeverBecomesReviewOrBooking() {
        val task = task()
        val reviewer = AppointmentMvpReviewReducer(task)
        val today = LocalDate.of(2027, 2, 10)
        assertNull(reviewer.observe("Może jakiś wolny termin?", today))
        assertNull(reviewer.observe("10.02.2027 o 08:30", today))
        assertNull(reviewer.observe("10.02.2027 o 20:00", today))
        val gate = CallCommitmentGate()
        assertFalse(gate.hasAuthorization())
        assertEquals("REVIEW_REQUIRED",
            reviewer.observe("10.02.2027 o 16:00", today)?.status)
        assertFalse(gate.hasAuthorization()) // no booking/provider/commitment issued
    }
}
