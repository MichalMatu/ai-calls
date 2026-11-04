package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test

class ParakeetPcm16DecoderTest {
    @Test fun decodeSplitLittleEndianSamplesAndCarry() {
        val value = Pcm16FloatDecoder()
        assertTrue(value.convert(byteArrayOf(0x00, 0x00, 0x01)).contentEquals(floatArrayOf(0f)))
        assertTrue(value.hasIncompleteSample())
        val sample = value.convert(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0xff.toByte(), 0x7f))
        assertEquals(3, sample.size)
        assertEquals(1f / 32768f, sample[0], 0.00001f)
        assertEquals(-1f, sample[1], 0.00001f)
        assertEquals(32767f / 32768f, sample[2], 0.00001f)
        assertFalse(value.hasIncompleteSample())
    }

    @Test fun modelIdIsExactOfficialStatefulFilename() {
        assertEquals("parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm", ParakeetModelFile.NAME)
    }
}
