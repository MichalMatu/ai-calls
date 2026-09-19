package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRelayTurnPacingTest {
    @Test
    fun `pcm duration reflects mono pcm16 at 16khz`() {
        assertEquals(1_000L, ChatRelayTurnPacing.pcmDurationMs(32_000))
        assertEquals(1_406L, ChatRelayTurnPacing.pcmDurationMs(44_994))
    }

    @Test
    fun `post tx hold covers full buffered speech plus guard`() {
        assertEquals(1_250L, ChatRelayTurnPacing.postTxHoldMs(32_000))
        assertEquals(1_656L, ChatRelayTurnPacing.postTxHoldMs(44_994))
    }

    @Test
    fun `post write hold subtracts blocking tx time while retaining guard`() {
        assertEquals(1_250L, ChatRelayTurnPacing.postWriteHoldMs(32_000, 0L))
        assertEquals(850L, ChatRelayTurnPacing.postWriteHoldMs(32_000, 400L))
        assertEquals(250L, ChatRelayTurnPacing.postWriteHoldMs(32_000, 1_200L))
    }
}
