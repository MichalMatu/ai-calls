package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSpeechProductionContractsTest {
    @Test
    fun telephonySpeechFormatIsFixedToMonoPcm16At16k() {
        assertEquals(16_000, LocalSpeechFormat.SAMPLE_RATE_HZ)
        assertEquals(1, LocalSpeechFormat.CHANNEL_COUNT)
        assertEquals(2, LocalSpeechFormat.BYTES_PER_SAMPLE)
        assertEquals(640, LocalSpeechFormat.bytesForDurationMs(20))
    }

    @Test
    fun generationGateInvalidatesOlderCallbacks() {
        val gate = LocalSpeechGenerationGate()
        val first = gate.begin()
        assertTrue(gate.isCurrent(first))

        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))

        gate.invalidate()
        assertFalse(gate.isCurrent(second))
    }
}
