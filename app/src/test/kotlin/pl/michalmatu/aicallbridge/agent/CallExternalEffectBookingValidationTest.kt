package pl.michalmatu.aicallbridge.agent

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallExternalEffectBookingValidationTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun at(hour: Int) = ZonedDateTime.of(2026, 11, 12, hour, 0, 0, 0, zone)
    private val proposed = CallProposal(at(10), null, null, "Synthetic clinic", null)
    private fun task(
        action: String = "BOOK_APPOINTMENT",
        constraints: CallConstraints = CallConstraints.unconstrained(),
        preferences: CallPreferences = CallPreferences.none(),
    ) = CallTask(
        "Synthetic clinic", action, "appointment", constraints, preferences, emptyMap(),
    )
    private fun result(
        t: CallTask = task(),
        reviewed: CallProposal = proposed,
        candidate: CallProposal = proposed,
        approved: Boolean = false,
    ) = CallExternalEffectValidator.validateBookAppointment(
        t, reviewed, CallExternalEffect.BookAppointment(candidate), approved,
    )
    private fun rejected(result: CallExternalEffectValidation) =
        (result as CallExternalEffectValidation.Rejected).reason

    @Test fun exactTaskAndProposalRequireNoRedundantUserConfirmation() {
        assertTrue(result() is CallExternalEffectValidation.Accepted)
        assertTrue(result(t = task(action = "book appointment")) is CallExternalEffectValidation.Accepted)
    }

    @Test fun wrongActionOrChangedOfferCannotReuseApprovedTerms() {
        assertEquals(
            CallExternalEffectValidation.RejectReason.TASK_ACTION_MISMATCH,
            rejected(result(t = task(action = "SEND_MESSAGE"))),
        )
        assertEquals(
            CallExternalEffectValidation.RejectReason.PROPOSAL_MISMATCH,
            rejected(result(candidate = proposed.copyWithHour(11))),
        )
    }

    @Test fun missingAppointmentTimeNeverBecomesACommit() {
        val withoutTime = CallProposal(null, null, null, null, null)
        assertEquals(
            CallExternalEffectValidation.RejectReason.APPOINTMENT_TIME_MISSING,
            rejected(result(reviewed = withoutTime, candidate = withoutTime)),
        )
    }

    @Test fun hardTimeWindowCannotBeOverriddenBySoftUserApproval() {
        val allowed = CallTimeWindow(at(10), at(11))
        val hard = CallConstraints(listOf(allowed), null, emptySet())
        val outOfScope = proposed.copyWithHour(12)
        assertEquals(
            CallExternalEffectValidation.RejectReason.HARD_CONSTRAINT_VIOLATION,
            rejected(result(t = task(constraints = hard), reviewed = outOfScope,
                candidate = outOfScope, approved = true)),
        )
    }

    @Test fun softPreferenceDeviationRequiresExistingExactUserDecision() {
        val preferred = CallTimeWindow(at(9), at(10))
        val prefs = CallPreferences(listOf(preferred), emptyList(), emptyList())
        val scoped = task(preferences = prefs)
        assertEquals(
            CallExternalEffectValidation.RejectReason.USER_DECISION_REQUIRED,
            rejected(result(t = scoped)),
        )
        assertTrue(result(t = scoped, approved = true) is CallExternalEffectValidation.Accepted)
    }

    @Test fun missingHardPriceOrPaymentTermRemainsRejected() {
        val priceConstraint = CallConstraints(
            emptyList(), MoneyAmount(java.math.BigDecimal("100"), "PLN"), emptySet(),
        )
        assertEquals(
            CallExternalEffectValidation.RejectReason.HARD_CONSTRAINT_VIOLATION,
            rejected(result(t = task(constraints = priceConstraint), approved = true)),
        )
    }

    private fun CallProposal.copyWithHour(hour: Int) =
        CallProposal(at(hour), price(), paymentMode(), provider(), location())
}
