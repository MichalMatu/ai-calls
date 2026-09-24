package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSetServiceExternalEffectTest {
    @Test
    fun clirEnableBindsTaskTargetPermitConsumptionAndExactExternalSuccess() {
        val target = orangeTarget()
        val effect = CallExternalEffect.SetService(target, CallService.CLIR, enabled = true)
        val validation = CallExternalEffectValidator.validateSetService(
            clirTask(enabled = true),
            target,
            effect,
        )
        val validatedEffect = (validation as CallExternalEffectValidation.Accepted).effect
        assertEquals(effect, validatedEffect)

        val gate = CallCommitmentGate { "clir-enable-permit" }
        val authorization = gate.authorize(validatedEffect)

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
            CallExternalEffect.SetService(target, CallService.CLIR, enabled = false),
        )
        assertTrue(completion.complete(wrongSuccess).isFailure)
        assertFalse(completion.isCompleted())

        val exactSuccess = CallExternalEffectSuccessEvidence(effect)
        assertEquals(effect, completion.complete(exactSuccess).getOrThrow())
        assertTrue(completion.isCompleted())
        assertTrue(completion.complete(exactSuccess).isFailure)
    }

    @Test
    fun clirValidatorRejectsTargetAndServiceValueWidening() {
        val target = orangeTarget()
        val task = clirTask(enabled = true)

        val wrongTarget = CallExternalEffect.SetService(
            CallResolvedTarget("Other operator", "+48999999999"),
            CallService.CLIR,
            enabled = true,
        )
        assertEquals(
            CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.TARGET_MISMATCH,
            ),
            CallExternalEffectValidator.validateSetService(task, target, wrongTarget),
        )

        val wrongValue = CallExternalEffect.SetService(target, CallService.CLIR, enabled = false)
        assertEquals(
            CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.SERVICE_VALUE_MISMATCH,
            ),
            CallExternalEffectValidator.validateSetService(task, target, wrongValue),
        )

        val missingValueTask = CallTask(
            "Orange",
            "SET_SERVICE",
            "CLIR",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        assertEquals(
            CallExternalEffectValidation.Rejected(
                CallExternalEffectValidation.RejectReason.MISSING_EXPLICIT_SERVICE_VALUE,
            ),
            CallExternalEffectValidator.validateSetService(
                missingValueTask,
                target,
                CallExternalEffect.SetService(target, CallService.CLIR, enabled = true),
            ),
        )
    }

    @Test
    fun clirPermitAndAppointmentPermitUseTheSameOneShotAuthorityStore() {
        val tokens = ArrayDeque(listOf("appointment-permit", "clir-permit"))
        val gate = CallCommitmentGate { tokens.removeFirst() }
        val appointment = appointmentProposal()

        val oldAuthorization = gate.authorize(appointment)
        val clirEffect = CallExternalEffect.SetService(
            orangeTarget(),
            CallService.CLIR,
            enabled = true,
        )
        val clirAuthorization = gate.authorize(clirEffect)

        assertTrue(gate.consumeEffect(oldAuthorization.value).isFailure)
        assertEquals(clirEffect, gate.consumeEffect(clirAuthorization.value).getOrThrow())
        assertFalse(gate.hasAuthorization())
    }

    @Test
    fun serviceEffectAndSuccessEvidenceDoNotLeakValuesThroughToString() {
        val effect = CallExternalEffect.SetService(
            orangeTarget(),
            CallService.CLIR,
            enabled = true,
        )
        val success = CallExternalEffectSuccessEvidence(effect)

        assertFalse(effect.toString().contains("CLIR"))
        assertFalse(effect.toString().contains("Orange"))
        assertFalse(success.toString().contains("CLIR"))
        assertFalse(success.toString().contains("true"))
    }

    private fun clirTask(enabled: Boolean) =
        CallTask(
            "Orange",
            "SET_SERVICE",
            "CLIR",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            mapOf(CallExternalEffectValidator.SERVICE_ENABLED_FACT to enabled.toString()),
        )

    private fun orangeTarget() = CallResolvedTarget("Orange", "+48123456789")

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
