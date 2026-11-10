package pl.michalmatu.aicallbridge.developerrelay

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
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

    @Test fun olderTurnEpochCannotContinueQueuedOrInflightPcm() {
        val epoch = AtomicLong(7L)
        val output = ObservedOutput()
        output.onWrite = { epoch.incrementAndGet() }
        assertEquals(640, AuthorizedPcmUplinkWriter.write(
            output, ByteArray(2_560), isAuthorized = { epoch.get() == 7L },
        ))
        assertEquals(listOf(640), output.frameSizes)
        assertFalse(output.flushed)
        assertEquals(0, AuthorizedPcmUplinkWriter.write(
            output, ByteArray(640), isAuthorized = { epoch.get() == 7L },
        ))
        val next = ObservedOutput()
        assertEquals(640, AuthorizedPcmUplinkWriter.write(
            next, ByteArray(640), isAuthorized = { epoch.get() == 8L },
        ))
        assertTrue(next.flushed)
    }

    @Test fun lastFrameRevocationCannotBeMisreportedAsCompleteTx() {
        val output = ObservedOutput()
        var authorized = true
        output.onWrite = { authorized = false }
        val result = AuthorizedPcmUplinkWriter.writeOutcome(
            output, ByteArray(640), isAuthorized = { authorized },
        )
        assertEquals(640, result.acceptedBytes)
        assertEquals(PcmUplinkWriteStatus.REVOKED, result.status)
        assertFalse(result.complete)
        assertFalse(output.flushed)
    }

    @Test fun flushFailureAfterAllAcceptedPcmRemainsUncertain() {
        val output = object : OutputStream() {
            var accepted = 0
            override fun write(b: Int) { accepted++ }
            override fun write(data: ByteArray, offset: Int, length: Int) { accepted += length }
            override fun flush() { throw java.io.IOException("simulated_flush_failure") }
        }
        val result = AuthorizedPcmUplinkWriter.writeOutcome(
            output, ByteArray(1280), isAuthorized = { true },
        )
        assertEquals(1280, result.acceptedBytes)
        assertEquals(1280, output.accepted)
        assertEquals(PcmUplinkWriteStatus.IO_UNCERTAIN, result.status)
        assertFalse(result.complete)
    }

    @Test fun ioErrorOnSecondFrameKeepsConservativeAcceptedPrefix() {
        val output = object : OutputStream() {
            var frames = 0
            override fun write(b: Int) = Unit
            override fun write(data: ByteArray, offset: Int, length: Int) {
                frames++
                if (frames == 2) throw java.io.IOException("synthetic_partial_write")
            }
        }
        val result = AuthorizedPcmUplinkWriter.writeOutcome(
            output, ByteArray(1920), isAuthorized = { true },
        )
        assertEquals(640, result.acceptedBytes)
        assertEquals(PcmUplinkWriteStatus.IO_UNCERTAIN, result.status)
        assertFalse(result.complete)
    }

    @Test fun authorizationRevokedInsideFlushInvalidatesFullFrameCount() {
        var authorized = true
        val output = object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun flush() { authorized = false }
        }
        val result = AuthorizedPcmUplinkWriter.writeOutcome(
            output, ByteArray(640), isAuthorized = { authorized },
        )
        assertEquals(640, result.acceptedBytes)
        assertEquals(PcmUplinkWriteStatus.REVOKED, result.status)
    }

    @Test fun authorizedCompleteResultStillFlushesAndCountsAllFrames() {
        val output = ObservedOutput()
        val result = AuthorizedPcmUplinkWriter.writeOutcome(
            output, ByteArray(1300), isAuthorized = { true },
        )
        assertEquals(1300, result.acceptedBytes)
        assertEquals(PcmUplinkWriteStatus.COMPLETE, result.status)
        assertTrue(result.complete)
        assertEquals(listOf(640, 640, 20), output.frameSizes)
        assertTrue(output.flushed)
    }

    @Test fun evidenceKeepsIoUncertaintyDistinctFromAuthorityRevocationAndPartialWrite() {
        assertEquals("WRITE_UNCERTAIN", PcmUplinkEvidenceStatus.of(
            PcmUplinkWriteStatus.IO_UNCERTAIN, 640, 1920))
        assertEquals("WRITE_UNCERTAIN", PcmUplinkEvidenceStatus.of(
            PcmUplinkWriteStatus.IO_UNCERTAIN, 1920, 1920))
        assertEquals("WRITE_REVOKED", PcmUplinkEvidenceStatus.of(
            PcmUplinkWriteStatus.REVOKED, 640, 1920))
        assertEquals("WRITE_REVOKED", PcmUplinkEvidenceStatus.of(
            PcmUplinkWriteStatus.REVOKED, 1920, 1920))
        assertEquals("WRITE_PARTIAL", PcmUplinkEvidenceStatus.of(
            PcmUplinkWriteStatus.COMPLETE, 640, 1920))
        assertEquals("WRITE_COMPLETE", PcmUplinkEvidenceStatus.of(
            PcmUplinkWriteStatus.COMPLETE, 1920, 1920))
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
