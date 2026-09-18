package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSpeechPcmTest {
    @Test
    fun decodesMonoPcm16Wave() {
        val wave = wave(sampleRate = 8_000, channels = 1, interleaved = shortArrayOf(0, 1000, -1000, 2000))
        val decoded = LocalSpeechPcm.decodeWaveToMonoPcm16(wave)

        assertEquals(8_000, decoded.sampleRate)
        assertEquals(1, decoded.sourceChannelCount)
        assertArrayEquals(shortArrayOf(0, 1000, -1000, 2000), decoded.monoSamples)
    }

    @Test
    fun downmixesStereoByAveragingChannels() {
        val wave = wave(sampleRate = 16_000, channels = 2, interleaved = shortArrayOf(1000, 3000, -2000, 2000))
        val decoded = LocalSpeechPcm.decodeWaveToMonoPcm16(wave)

        assertEquals(2, decoded.sourceChannelCount)
        assertArrayEquals(shortArrayOf(2000, 0), decoded.monoSamples)
    }

    @Test
    fun resamplesAndSerializesPcm16LittleEndian() {
        val resampled = LocalSpeechPcm.resampleLinear(shortArrayOf(0, 1000, 2000, 3000), 8_000, 16_000)
        assertEquals(8, resampled.size)
        assertEquals(0, resampled.first().toInt())
        assertEquals(3000, resampled.last().toInt())

        assertArrayEquals(
            byteArrayOf(0x34, 0x12, 0xCC.toByte(), 0xED.toByte()),
            LocalSpeechPcm.toLittleEndianBytes(shortArrayOf(0x1234, -0x1234)),
        )
    }

    private fun wave(sampleRate: Int, channels: Int, interleaved: ShortArray): ByteArray {
        val dataLength = interleaved.size * 2
        val bytes = ByteArray(44 + dataLength)
        putAscii(bytes, 0, "RIFF")
        putU32(bytes, 4, 36 + dataLength)
        putAscii(bytes, 8, "WAVE")
        putAscii(bytes, 12, "fmt ")
        putU32(bytes, 16, 16)
        putU16(bytes, 20, 1)
        putU16(bytes, 22, channels)
        putU32(bytes, 24, sampleRate)
        putU32(bytes, 28, sampleRate * channels * 2)
        putU16(bytes, 32, channels * 2)
        putU16(bytes, 34, 16)
        putAscii(bytes, 36, "data")
        putU32(bytes, 40, dataLength)
        interleaved.forEachIndexed { index, sample -> putU16(bytes, 44 + index * 2, sample.toInt() and 0xffff) }
        return bytes
    }

    private fun putAscii(bytes: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
