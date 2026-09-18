package pl.michalmatu.aicallbridge.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeResponseDoneProtocolTest {
    private val protocol = RealtimeWebSocketProtocol()

    @Test
    fun parsesCompletedCancelledFailedAndIncompleteResponseDone() {
        assertDone("completed", RealtimeResponseStatus.COMPLETED)
        assertDone("cancelled", RealtimeResponseStatus.CANCELLED)
        assertDone("failed", RealtimeResponseStatus.FAILED)
        assertDone("incomplete", RealtimeResponseStatus.INCOMPLETE)
    }

    @Test
    fun unknownResponseDoneStatusIsPreservedAsUnknownForFailClosedPolicy() {
        assertDone("future_status", RealtimeResponseStatus.UNKNOWN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun responseDoneRejectsMissingResponseId() {
        protocol.parseServerEvent(
            """{"type":"response.done","response":{"status":"completed"}}""",
            1L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun responseDoneRejectsMissingStatus() {
        protocol.parseServerEvent(
            """{"type":"response.done","response":{"id":"resp_1"}}""",
            1L,
        )
    }

    private fun assertDone(rawStatus: String, expected: RealtimeResponseStatus) {
        val event = protocol.parseServerEvent(
            """{"type":"response.done","response":{"id":"resp_1","status":"$rawStatus"}}""",
            1L,
        )

        assertEquals(RealtimeServerEvent.Type.RESPONSE_DONE, event.type())
        assertEquals("resp_1", event.responseId())
        assertEquals(expected, event.responseStatus())
    }
}
