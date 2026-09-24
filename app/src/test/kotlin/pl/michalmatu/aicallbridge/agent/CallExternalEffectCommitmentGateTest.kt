package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallExternalEffectCommitmentGateTest {
    @Test
    fun typedBookAppointmentEffectCanBeAuthorizedAndConsumedExactlyOnce() {
        val gate = CallCommitmentGate { "typed-effect-permit" }
        val proposal = proposal("Clinic A", "150.00")
        val effect = CallExternalEffect.BookAppointment(proposal)

        val authorization = gate.authorize(effect)
        val consumed = gate.consumeEffect(authorization.value)

        assertTrue(consumed.isSuccess)
        assertEquals(effect, consumed.getOrThrow())
        assertFalse(gate.hasAuthorization())
        assertTrue(gate.consumeEffect(authorization.value).isFailure)
    }

    @Test
    fun proposalCompatibilityAdapterAndTypedApiShareOneAuthorizationStore() {
        val tokens = ArrayDeque(listOf("legacy-to-typed", "typed-to-legacy"))
        val gate = CallCommitmentGate { tokens.removeFirst() }
        val proposal = proposal("Clinic A", "150.00")

        val legacyAuthorization = gate.authorize(proposal)
        assertEquals(
            CallExternalEffect.BookAppointment(proposal),
            gate.consumeEffect(legacyAuthorization.value).getOrThrow(),
        )

        val typedAuthorization = gate.authorize(CallExternalEffect.BookAppointment(proposal))
        assertEquals(proposal, gate.consume(typedAuthorization.value).getOrThrow())
        assertFalse(gate.hasAuthorization())
    }

    @Test
    fun consumptionEvidenceCarriesTypedEffectAndPreservesAppointmentCompatibilityView() {
        val proposal = proposal("Clinic A", "150.00")
        val effect = CallExternalEffect.BookAppointment(proposal)

        val typedEvidence = CallCommitmentConsumptionEvidence(effect)
        val legacyEvidence = CallCommitmentConsumptionEvidence(proposal)

        assertEquals(effect, typedEvidence.effect)
        assertEquals(proposal, typedEvidence.proposal)
        assertEquals(effect, legacyEvidence.effect)
        assertEquals(proposal, legacyEvidence.proposal)
        assertFalse(typedEvidence.toString().contains("Clinic A"))
    }

    private fun proposal(provider: String, amount: String) =
        CallProposal(
            null,
            MoneyAmount(BigDecimal(amount), "PLN"),
            CallPaymentMode.PRIVATE,
            provider,
            "Wroclaw",
        )
}
