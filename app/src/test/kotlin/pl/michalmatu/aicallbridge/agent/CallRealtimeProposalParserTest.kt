package pl.michalmatu.aicallbridge.agent

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRealtimeProposalParserTest {
    @Test
    fun parsesStrictCompleteProposal() {
        val proposal = CallRealtimeProposalParser.parse(
            "{\"scheduled_at\":null,\"price\":{\"amount\":\"150.00\",\"currency\":\"PLN\"},\"payment_mode\":\"PRIVATE\",\"provider\":\"Clinic A\",\"location\":\"Wroclaw\"}",
        )
        assertNull(proposal.scheduledAt())
        assertEquals(BigDecimal("150.00"), proposal.price().amount())
        assertEquals("PLN", proposal.price().currencyCode())
        assertEquals(CallPaymentMode.PRIVATE, proposal.paymentMode())
        assertEquals("Clinic A", proposal.provider())
        assertEquals("Wroclaw", proposal.location())
    }

    @Test
    fun rejectsDuplicateUnknownMissingAndTrailingData() {
        val invalid = listOf(
            "{\"scheduled_at\":null,\"scheduled_at\":null,\"price\":null,\"payment_mode\":null,\"provider\":null,\"location\":null}",
            "{\"scheduled_at\":null,\"price\":null,\"payment_mode\":null,\"provider\":null,\"location\":null,\"extra\":\"no\"}",
            "{\"scheduled_at\":null}",
            "{\"scheduled_at\":null,\"price\":null,\"payment_mode\":null,\"provider\":null,\"location\":null} {}",
        )
        invalid.forEach { json ->
            assertFails<IllegalArgumentException> { CallRealtimeProposalParser.parse(json) }
        }
    }

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
