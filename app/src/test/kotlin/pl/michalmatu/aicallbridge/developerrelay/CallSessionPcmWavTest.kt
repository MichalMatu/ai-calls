package pl.michalmatu.aicallbridge.developerrelay

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionPcmWavTest {
    @Test fun wavHeaderDescribesExact16kMonoPcm() {
        val file = File.createTempFile("private-rxtx-", ".wav")
        try {
            val pcm = ByteArray(640) { (it % 128).toByte() }
            CallSessionPcmWav.write(file, pcm)
            val bytes = file.readBytes()
            assertEquals(44 + pcm.size, bytes.size)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(bytes.size - 8, header.getInt(4))
            assertEquals(1, header.getShort(20).toInt()) // PCM
            assertEquals(1, header.getShort(22).toInt()) // mono
            assertEquals(16_000, header.getInt(24))
            assertEquals(32_000, header.getInt(28)) // bytes per second
            assertEquals(16, header.getShort(34).toInt())
            assertEquals(pcm.size, header.getInt(40))
            assertTrue(bytes.copyOfRange(44, bytes.size).contentEquals(pcm))
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOddLengthPcmInsteadOfSilentlyCorruptingWave() {
        val file = File.createTempFile("invalid-rxtx-", ".wav")
        try { CallSessionPcmWav.write(file, ByteArray(3)) }
        finally { file.delete() }
    }
}
