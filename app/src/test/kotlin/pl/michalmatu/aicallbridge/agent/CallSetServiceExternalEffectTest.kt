package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSetServiceExternalEffectTest {
    @Test
    fun clirEnableRequiresTypedPermitConsumptionAndExactExternalSuccess() {
        val gate = CallCommitmentGate { "clir-enable-permit" }
        val effect = CallExternalEffect.SetService(CallService.CLIR, enabled = true)
        val authorization = gate.authorize(effect)

        // Legacy BOOK_APPOINTMENT execution must not consume a non-appointment permit.
        assertTrue(gate.consume(authorization.value).isFailure)
        assertTrue(gate.hasAuthorization())

        val consumedEffect = gate.consumeEffect(authorization.value).getOrThrow()
        assertEquals(effect, consumedEffect)
        assertFalse(gate.hasAuthorization())

        val consumption = CallCommitmentConsumptionEvidence(consumedEffect)
        assertFails<IllegalStateException> { consumption.proposal }
        val completion = CallExternalEffectCompletionTracker.fromConsumption(consumption)

        val wrongSuccess = CallExternalEffectSuccessEvidence(
            CallExternalEffect.SetService(CallService.CLIR, enabled = false),
        )
        assertTrue(completion.complete(wrongSuccess).isFailure)
        assertFalse(completion.isCompleted())

        val exactSuccess = CallExternalEffectSuccessEvidence(effect)
        assertEquals(effect, completion.complete(exactSuccess).getOrThrow())
        assertTrue(completion.isCompleted())
        assertTrue(completion.complete(exactSuccess).isFailure)
    }

    @Test
    fun clirPermitAndAppointmentPermitUseTheSameOneShotAuthorityStore() {
        val tokens = ArrayDeque(listOf("appointment-permit", "clir-permit"))
        val gate = CallCommitmentGate { tokens.removeFirst() }
        val appointment = appointmentProposal()

        val oldAuthorization = gate.authorize(appointment)
        val clirEffect = CallExternalEffect.SetService(CallService.CLIR, enabled = true)
        val clirAuthorization = gate.authorize(clirEffect)

        assertTrue(gate.consumeEffect(oldAuthorization.value).isFailure)
        assertEquals(clirEffect, gate.consumeEffect(clirAuthorization.value).getOrThrow())
        assertFalse(gate.hasAuthorization())
    }

    @Test
    fun serviceEffectAndSuccessEvidenceDoNotLeakValuesThroughToString() {
        val effect = CallExternalEffect.SetService(CallService.CLIR, enabled = true)
        val success = CallExternalEffectSuccessEvidence(effect)

        assertFalse(effect.toString().contains("CLIR"))
        assertFalse(success.toString().contains("CLIR"))
        assertFalse(success.toString().contains("true"))
    }

    private fun appointmentProposal() =
        CallProposal(
            null,
            MoneyAmount(BigDecimal("150.00"), "PLN"),
            CallPaymentMode.PRIVATE,
            "Clinic A",
            "Wroclaw",
        )

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError(
                "expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}",
                error,
            )
        }
        throw AssertionError("expected ${T::class.java.simpleName}")
    }
}
