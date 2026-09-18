package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.session.CallRealtimeOutputDecision

class RealtimeLiveCallSmokeSpecTest {
    @Test
    fun diagnosticSessionHasNoToolsOrCommitmentSurface() {
        val request = RealtimeLiveCallSmokeSpec.request()

        assertTrue(request.tools.isEmpty())
        assertTrue(request.instructions.contains("Do not make commitments"))
        assertTrue(request.instructions.contains("No tools are available"))
    }

    @Test
    fun durationIsStrictlyBounded() {
        assertEquals(3_000L, RealtimeLiveCallSmokeSpec.validateDurationMs(3_000L))
        assertEquals(30_000L, RealtimeLiveCallSmokeSpec.validateDurationMs(30_000L))
        assertFails<IllegalArgumentException> { RealtimeLiveCallSmokeSpec.validateDurationMs(2_999L) }
        assertFails<IllegalArgumentException> { RealtimeLiveCallSmokeSpec.validateDurationMs(30_001L) }
    }

    @Test
    fun diagnosticSpeechStillUsesExplicitOutputPolicy() {
        val decision = RealtimeLiveCallSmokeSpec.outputApprovalPolicy.evaluate(
            RealtimeOutputPartId("response", "item", 0, 0),
            "neutral diagnostic response",
        )

        assertEquals(CallRealtimeOutputDecision.RELEASE, decision)
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
