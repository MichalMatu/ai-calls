package pl.michalmatu.aicallbridge.realtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

public final class RealtimeWebSocketProtocolTest {
    private static final PcmFormat REALTIME_PCM = new PcmFormat(24_000, 1, 16);

    private final RealtimeWebSocketProtocol protocol = new RealtimeWebSocketProtocol();

    @Test
    public void sessionUpdateUsesCurrentGaRealtimeAudioShapeWithoutEmbeddingSecret() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_super_secret", 9_999L);
        RealtimeSessionConfig config = new RealtimeSessionConfig(
            "wss://api.openai.com/v1/realtime",
            secret,
            "gpt-realtime-2.1",
            "Stay inside the user's explicit authority."
        );

        String json = protocol.sessionUpdate(config);

        assertTrue(json.contains("\"type\":\"session.update\""));
        assertTrue(json.contains("\"type\":\"realtime\""));
        assertTrue(json.contains("\"model\":\"gpt-realtime-2.1\""));
        assertTrue(json.contains("\"output_modalities\":[\"audio\"]"));
        assertTrue(json.contains("\"type\":\"audio/pcm\""));
        assertTrue(json.contains("\"rate\":24000"));
        assertTrue(json.contains("\"turn_detection\":{\"type\":\"semantic_vad\"}"));
        assertTrue(json.contains("Stay inside the user's explicit authority."));
        assertFalse(json.contains("eph_super_secret"));
    }

    @Test
    public void inputAudioAppendCarriesBase64Pcm24kOnly() {
        byte[] pcm = "pcm-bytes".getBytes(StandardCharsets.UTF_8);
        if ((pcm.length & 1) != 0) {
            pcm = java.util.Arrays.copyOf(pcm, pcm.length + 1);
        }
        PcmFrame frame = new PcmFrame(REALTIME_PCM, pcm, 123L);

        String json = protocol.inputAudioAppend(frame);

        assertTrue(json.contains("\"type\":\"input_audio_buffer.append\""));
        assertTrue(json.contains("\"audio\":\"" + Base64.getEncoder().encodeToString(pcm) + "\""));
    }

    @Test
    public void outputAudioDeltaDecodesToPcm24kFrame() {
        byte[] pcm = new byte[] {1, 2, 3, 4, 5, 6};
        String json = "{\"type\":\"response.output_audio.delta\",\"delta\":\""
            + Base64.getEncoder().encodeToString(pcm)
            + "\"}";

        RealtimeServerEvent event = protocol.parseServerEvent(json, 555L);

        assertEquals(RealtimeServerEvent.Type.AUDIO_DELTA, event.type());
        assertEquals(REALTIME_PCM, event.audioFrame().getFormat());
        assertArrayEquals(pcm, event.audioFrame().getData());
        assertEquals(555L, event.audioFrame().getMonotonicTimestampNs());
    }

    @Test
    public void speechLifecycleAndErrorsAreDecodedWithoutTreatingUnknownEventsAsFatal() {
        assertEquals(
            RealtimeServerEvent.Type.REMOTE_SPEECH_STARTED,
            protocol.parseServerEvent("{\"type\":\"input_audio_buffer.speech_started\"}", 1L).type()
        );
        assertEquals(
            RealtimeServerEvent.Type.REMOTE_SPEECH_STOPPED,
            protocol.parseServerEvent("{\"type\":\"input_audio_buffer.speech_stopped\"}", 1L).type()
        );

        RealtimeServerEvent error = protocol.parseServerEvent(
            "{\"type\":\"error\",\"error\":{\"message\":\"bad request\"}}",
            1L
        );
        assertEquals(RealtimeServerEvent.Type.ERROR, error.type());
        assertEquals("bad request", error.errorMessage());

        RealtimeServerEvent other = protocol.parseServerEvent(
            "{\"type\":\"future.event.we.do.not.know.yet\",\"x\":1}",
            1L
        );
        assertEquals(RealtimeServerEvent.Type.OTHER, other.type());
        assertEquals("future.event.we.do.not.know.yet", other.rawType());
    }

    @Test
    public void cancelResponseUsesCurrentGaEventName() {
        assertEquals("{\"type\":\"response.cancel\"}", protocol.responseCancel());
    }
}
