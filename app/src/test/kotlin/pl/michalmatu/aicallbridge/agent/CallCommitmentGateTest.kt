package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCommitmentGateTest {
    @Test
    fun exactAuthorizationCanBeConsumedOnlyOnce() {
        val tokens = ArrayDeque(listOf("permit-1"))
        val gate = CallCommitmentGate { tokens.removeFirst() }
        val proposal = proposal("Clinic A", "150.00")

        val authorization = gate.authorize(proposal)

        assertEquals("permit-1", authorization.value)
        assertFalse(authorization.toString().contains("permit-1"))
        assertTrue(gate.hasAuthorization())
        assertTrue(gate.consume("wrong-token").isFailure)
        assertTrue(gate.hasAuthorization())

        val consumed = gate.consume("permit-1")

        assertTrue(consumed.isSuccess)
        assertEquals(proposal, consumed.getOrThrow())
        assertFalse(gate.hasAuthorization())
        assertTrue(gate.consume("permit-1").isFailure)
    }

    @Test
    fun newerAuthorizationInvalidatesOlderProposalAuthorization() {
        val tokens = ArrayDeque(listOf("permit-old", "permit-new"))
        val gate = CallCommitmentGate { tokens.removeFirst() }
        val oldProposal = proposal("Clinic A", "150.00")
        val newProposal = proposal("Clinic B", "175.00")

        gate.authorize(oldProposal)
        val latest = gate.authorize(newProposal)

        assertTrue(gate.consume("permit-old").isFailure)
        assertEquals(newProposal, gate.consume(latest.value).getOrThrow())
    }

    @Test
    fun clearRevokesOutstandingAuthorization() {
        val gate = CallCommitmentGate { "permit-clear" }
        val authorization = gate.authorize(proposal("Clinic A", "150.00"))

        gate.clear()

        assertFalse(gate.hasAuthorization())
        assertTrue(gate.consume(authorization.value).isFailure)
    }

    @Test
    fun invalidGeneratedTokensFailClosed() {
        val proposal = proposal("Clinic A", "150.00")

        assertFails<IllegalArgumentException> {
            CallCommitmentGate { " " }.authorize(proposal)
        }
        assertFails<IllegalArgumentException> {
            CallCommitmentGate { "contains space" }.authorize(proposal)
        }
        assertFails<IllegalArgumentException> {
            CallCommitmentGate { "x".repeat(257) }.authorize(proposal)
        }
    }

    private fun proposal(provider: String, amount: String) =
        CallProposal(
            null,
            MoneyAmount(BigDecimal(amount), "PLN"),
            CallPaymentMode.PRIVATE,
            provider,
            "Wroclaw",
        )

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
        }
        throw AssertionError("expected ${T::class.java.simpleName}")
    }
}
