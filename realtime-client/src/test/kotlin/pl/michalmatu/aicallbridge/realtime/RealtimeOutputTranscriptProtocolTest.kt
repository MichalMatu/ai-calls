package pl.michalmatu.aicallbridge.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RealtimeOutputTranscriptProtocolTest {
    private val protocol = RealtimeWebSocketProtocol()

    @Test
    fun audioDeltaCarriesStableOutputPartIdentity() {
        val event = protocol.parseServerEvent(
            """{"type":"response.output_audio.delta","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":2,"delta":"AQACAA=="}""",
            123L,
        )

        assertEquals(RealtimeServerEvent.Type.AUDIO_DELTA, event.type())
        assertEquals(RealtimeOutputPartId("resp_1", "item_1", 0, 2), event.outputPartId())
        assertNotNull(event.audioFrame())
    }

    @Test
    fun parsesOutputAudioTranscriptDeltaAndDoneForSamePart() {
        val delta = protocol.parseServerEvent(
            """{"type":"response.output_audio_transcript.delta","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":2,"delta":"Potwier"}""",
            1L,
        )
        val done = protocol.parseServerEvent(
            """{"type":"response.output_audio_transcript.done","response_id":"resp_1","item_id":"item_1","output_index":0,"content_index":2,"transcript":"Potwierdzam termin."}""",
            2L,
        )

        assertEquals(RealtimeServerEvent.Type.OUTPUT_AUDIO_TRANSCRIPT_DELTA, delta.type())
        assertEquals(RealtimeOutputPartId("resp_1", "item_1", 0, 2), delta.outputPartId())
        assertEquals("Potwier", delta.text())

        assertEquals(RealtimeServerEvent.Type.OUTPUT_AUDIO_TRANSCRIPT_DONE, done.type())
        assertEquals(delta.outputPartId(), done.outputPartId())
        assertEquals("Potwierdzam termin.", done.text())
    }

    @Test
    fun parsesOutputAudioDoneForTheSamePart() {
        val event = protocol.parseServerEvent(
            """{"type":"response.output_audio.done","response_id":"resp_9","item_id":"item_4","output_index":1,"content_index":0}""",
            3L,
        )

        assertEquals(RealtimeServerEvent.Type.OUTPUT_AUDIO_DONE, event.type())
        assertEquals(RealtimeOutputPartId("resp_9", "item_4", 1, 0), event.outputPartId())
    }

    @Test(expected = IllegalArgumentException::class)
    fun transcriptEventRejectsMissingResponseIdentity() {
        protocol.parseServerEvent(
            """{"type":"response.output_audio_transcript.done","item_id":"item_1","output_index":0,"content_index":0,"transcript":"text"}""",
            4L,
        )
    }
}
