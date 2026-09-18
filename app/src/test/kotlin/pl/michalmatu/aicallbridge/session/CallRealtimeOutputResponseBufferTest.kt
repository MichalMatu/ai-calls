package pl.michalmatu.aicallbridge.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.audio.PcmFormat
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId

class CallRealtimeOutputResponseBufferTest {
    private val format = PcmFormat(24_000, 1, 16)
    private val id = RealtimeOutputPartId("resp_1", "item_1", 0, 0)

    @Test
    fun releasesOnlyAfterAudioAndFinalTranscriptAreComplete() {
        val seen = mutableListOf<String>()
        val buffer = CallRealtimeOutputResponseBuffer(
            approvalPolicy = CallRealtimeOutputApprovalPolicy { _, transcript ->
                seen += transcript
                CallRealtimeOutputDecision.RELEASE
            },
        )
        val source = byteArrayOf(1, 0, 2, 0)

        assertEquals(
            CallRealtimeOutputBufferResult.Pending,
            buffer.onAudio(id, PcmFrame(format, source, 10L)),
        )
        source[0] = 99
        assertEquals(
            CallRealtimeOutputBufferResult.Pending,
            buffer.onTranscriptDelta(id, "Dzień "),
        )
        assertEquals(
            CallRealtimeOutputBufferResult.Pending,
            buffer.onTranscriptDelta(id, "dobry"),
        )
        assertEquals(
            CallRealtimeOutputBufferResult.Pending,
            buffer.onTranscriptDone(id, "Dzień dobry"),
        )

        val result = buffer.onAudioDone(id)
        assertTrue(result is CallRealtimeOutputBufferResult.Released)
        val output = (result as CallRealtimeOutputBufferResult.Released).output
        assertEquals("Dzień dobry", output.transcript)
        assertEquals(listOf("Dzień dobry"), seen)
        assertEquals(1, output.frames.size)
        assertEquals(1, output.frames.single().data[0].toInt())
        assertEquals(0L, buffer.snapshot().bufferedAudioBytes)
        assertEquals(0, buffer.snapshot().pendingParts)
    }

    @Test
    fun audioDoneFirstStillWaitsForFinalTranscript() {
        val buffer = releasingBuffer()
        buffer.onAudio(id, frame(4))

        assertEquals(CallRealtimeOutputBufferResult.Pending, buffer.onAudioDone(id))
        val result = buffer.onTranscriptDone(id, "hello")

        assertTrue(result is CallRealtimeOutputBufferResult.Released)
    }

    @Test
    fun policyCanDropCompleteResponseWithoutReleasingPcm() {
        val buffer = CallRealtimeOutputResponseBuffer(
            approvalPolicy = CallRealtimeOutputApprovalPolicy { _, _ ->
                CallRealtimeOutputDecision.DROP
            },
        )
        buffer.onAudio(id, frame(8))
        buffer.onTranscriptDone(id, "not allowed")

        val result = buffer.onAudioDone(id)

        assertEquals(
            CallRealtimeOutputBufferResult.Dropped(id, "not allowed"),
            result,
        )
        assertEquals(0L, buffer.snapshot().bufferedAudioBytes)
    }

    @Test
    fun finalTranscriptIsAuthoritativeEvenWhenOnlyPartialDeltasWereObserved() {
        val seen = mutableListOf<String>()
        val buffer = CallRealtimeOutputResponseBuffer(
            approvalPolicy = CallRealtimeOutputApprovalPolicy { _, transcript ->
                seen += transcript
                CallRealtimeOutputDecision.RELEASE
            },
        )
        buffer.onAudio(id, frame(4))
        buffer.onTranscriptDelta(id, "Dzień ")
        buffer.onTranscriptDone(id, "Dzień dobry")

        val result = buffer.onAudioDone(id)

        assertTrue(result is CallRealtimeOutputBufferResult.Released)
        assertEquals(listOf("Dzień dobry"), seen)
        assertEquals("Dzień dobry", (result as CallRealtimeOutputBufferResult.Released).output.transcript)
    }

    @Test
    fun oversizedAudioAndTooManyConcurrentPartsFailClosed() {
        val small = CallRealtimeOutputResponseBuffer(
            approvalPolicy = CallRealtimeOutputApprovalPolicy { _, _ -> CallRealtimeOutputDecision.RELEASE },
            maxBufferedAudioBytes = 8,
            maxPendingParts = 2,
        )
        small.onAudio(id, frame(8))
        assertFails<IllegalStateException> {
            small.onAudio(id, frame(2))
        }

        val parts = CallRealtimeOutputResponseBuffer(
            approvalPolicy = CallRealtimeOutputApprovalPolicy { _, _ -> CallRealtimeOutputDecision.RELEASE },
            maxBufferedAudioBytes = 100,
            maxPendingParts = 1,
        )
        parts.onAudio(id, frame(2))
        assertFails<IllegalStateException> {
            parts.onAudio(RealtimeOutputPartId("resp_2", "item_2", 0, 0), frame(2))
        }
    }

    @Test
    fun finalizedPartRejectsLateEventsUntilBufferIsCleared() {
        val buffer = releasingBuffer()
        buffer.onAudio(id, frame(4))
        buffer.onTranscriptDone(id, "done")
        assertTrue(buffer.onAudioDone(id) is CallRealtimeOutputBufferResult.Released)

        assertFails<IllegalStateException> { buffer.onAudio(id, frame(2)) }

        buffer.clear()
        assertEquals(CallRealtimeOutputBufferResult.Pending, buffer.onAudio(id, frame(2)))
    }

    private fun releasingBuffer() = CallRealtimeOutputResponseBuffer(
        approvalPolicy = CallRealtimeOutputApprovalPolicy { _, _ -> CallRealtimeOutputDecision.RELEASE },
    )

    private fun frame(byteCount: Int): PcmFrame =
        PcmFrame(format, ByteArray(byteCount) { (it and 0x7f).toByte() }, 1L)

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
