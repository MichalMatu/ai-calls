package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class GemmaAudioPcm16Test {
    @Test fun wrapsPcm16AsLittleEndianWaveWithoutEncodingChange() {
        val samples = byteArrayOf(0, -128, -1, 127)
        val wav = GemmaAudioPcm16.toWave(samples)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals(16_000, (wav[24].toInt() and 255) or ((wav[25].toInt() and 255) shl 8))
        assertArrayEquals(samples, wav.copyOfRange(44, 48))
        assertEquals(48, wav.size)
    }

    @Test fun rejectsMisalignedAndOverlongAudio() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1), ByteArray(GemmaAudioPcm16.MAX_PCM_BYTES + 2))) {
            try {
                GemmaAudioPcm16.toWave(bytes)
                fail("must reject")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
