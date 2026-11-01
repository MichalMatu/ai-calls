package pl.michalmatu.aicallbridge.developerrelay

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedPcmUplinkWriterTest {
    @Test fun fullAuthorizedPcmIsSentInFramesAndFlushed() {
        val pcm = ByteArray(1300) { it.toByte() }
        val output = ObservedOutput()
        val bytes = AuthorizedPcmUplinkWriter.write(output, pcm, isAuthorized = { true })
        assertEquals(pcm.size, bytes)
        assertArrayEquals(pcm, output.bytes.toByteArray())
        assertEquals(listOf(640, 640, 20), output.frameSizes)
        assertTrue(output.flushed)
    }

    @Test fun revokedConsentStopsBeforeNextFrameAndDoesNotFlush() {
        val output = ObservedOutput()
        var allowed = true
        output.onWrite = { allowed = false }
        val bytes = AuthorizedPcmUplinkWriter.write(
            output, ByteArray(10_000), isAuthorized = { allowed },
        )
        assertEquals(640, bytes)
        assertEquals(listOf(640), output.frameSizes)
        assertFalse(output.flushed)
    }

    @Test fun revokedBeforeFirstFrameSendsNothing() {
        val output = ObservedOutput()
        assertEquals(
            0,
            AuthorizedPcmUplinkWriter.write(
                output, ByteArray(640), isAuthorized = { false },
            ),
        )
        assertEquals(0, output.bytes.size())
        assertFalse(output.flushed)
    }

    @Test fun invalidAlignmentFailsClosed() {
        val output = ObservedOutput()
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedPcmUplinkWriter.write(output, ByteArray(3), isAuthorized = { true })
        }
        assertEquals(0, output.bytes.size())
    }

    private class ObservedOutput : OutputStream() {
        val bytes = ByteArrayOutputStream()
        val frameSizes = mutableListOf<Int>()
        var flushed = false
        var onWrite: () -> Unit = {}

        override fun write(b: Int) { bytes.write(b) }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            frameSizes += length
            bytes.write(buffer, offset, length)
            onWrite()
        }
        override fun flush() { flushed = true }
    }
}
