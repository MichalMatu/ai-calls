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


    @Test fun waveHeaderSizesAndFormatAreExactForArbitraryPcmLengths() {
        for (size in listOf(2, 4, 160, 32_000, GemmaAudioPcm16.MAX_PCM_BYTES)) {
            val pcm = ByteArray(size) { (it and 255).toByte() }
            val wav = GemmaAudioPcm16.toWave(pcm)
            fun le32(offset: Int): Long = (0..3).fold(0L) { sum, shift ->
                sum or ((wav[offset + shift].toLong() and 255L) shl (8 * shift))
            }
            fun le16(offset: Int): Int =
                (wav[offset].toInt() and 255) or ((wav[offset + 1].toInt() and 255) shl 8)
            assertEquals(size + 44, wav.size)
            assertEquals((size + 36).toLong(), le32(4))
            assertEquals("WAVEfmt ", String(wav.copyOfRange(8, 16), Charsets.US_ASCII))
            assertEquals(16L, le32(16))
            assertEquals(1, le16(20))
            assertEquals(1, le16(22))
            assertEquals(16_000L, le32(24))
            assertEquals(32_000L, le32(28))
            assertEquals(2, le16(32))
            assertEquals(16, le16(34))
            assertEquals("data", String(wav.copyOfRange(36, 40), Charsets.US_ASCII))
            assertEquals(size.toLong(), le32(40))
            assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
        }
    }

    @Test fun conversionDoesNotModifyOwnedPcmAndProducesDeterministicBytes() {
        val pcm = ByteArray(32_000) { ((it * 31) and 255).toByte() }
        val snapshot = pcm.copyOf()
        val first = GemmaAudioPcm16.toWave(pcm)
        val second = GemmaAudioPcm16.toWave(pcm)
        assertArrayEquals(snapshot, pcm)
        assertArrayEquals(first, second)
        assertArrayEquals(snapshot, first.copyOfRange(44, first.size))
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
