package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public final class RtpPcmuCodecTest {
    @Test public void knownSilenceCodewordAndRtpHeaderAreStable() {
        byte[] silence = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
        byte[] packet = RtpPcmuCodec.packetize(
            silence, 0, 65535, 0xffff_fff0L, 0x12345678
        );
        assertEquals(RtpPcmuCodec.PACKET_BYTES, packet.length);
        assertEquals(0x80, packet[0] & 0xff);
        assertEquals(0, packet[1] & 0xff);
        assertEquals(0xff, packet[2] & 0xff);
        assertEquals(0xff, packet[3] & 0xff);
        assertEquals(0x12, packet[8] & 0xff);
        for (int i = 12; i < packet.length; i++) {
            assertEquals("PCMU silence at " + i, 0xff, packet[i] & 0xff);
        }
        RtpPcmuCodec.Frame decoded = RtpPcmuCodec.parse(packet, packet.length, 0x12345678);
        assertNotNull(decoded);
        assertEquals(65535, decoded.sequence());
        assertEquals(0xffff_fff0L, decoded.timestamp());
        assertArrayEquals(silence, decoded.pcm16());
    }

    @Test public void negativeAndPositiveSpeechPcmSurviveLossyCompanding() {
        byte[] samples = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
        for (int i = 0; i < RtpPcmuCodec.FRAME_SAMPLES; i++) {
            short value = (short) ((i - 80) * 350);
            samples[i * 2] = (byte) value;
            samples[i * 2 + 1] = (byte) (value >>> 8);
        }
        RtpPcmuCodec.Frame frame = RtpPcmuCodec.parse(
            RtpPcmuCodec.packetize(samples, 0, 1, 160, -1),
            RtpPcmuCodec.PACKET_BYTES, -1
        );
        assertNotNull(frame);
        for (int i = 0; i < RtpPcmuCodec.FRAME_SAMPLES; i++) {
            int source = (short) (((samples[i * 2 + 1] & 0xff) << 8)
                | (samples[i * 2] & 0xff));
            byte[] out = frame.pcm16();
            int result = (short) (((out[i * 2 + 1] & 0xff) << 8) | (out[i * 2] & 0xff));
            assertTrue("signed sample preserved", source == 0 ||
                Integer.signum(source) == Integer.signum(result));
            assertTrue("bounded G711 quantization", Math.abs(source - result) < 1400);
        }
    }

    @Test public void rejectsInvalidHeadersWrongStreamAndTruncatedPayload() {
        byte[] packet = RtpPcmuCodec.packetize(
            new byte[RtpPcmuCodec.FRAME_PCM_BYTES], 0, 1, 0, 99
        );
        assertNull(RtpPcmuCodec.parse(packet, packet.length, 100));
        assertNull(RtpPcmuCodec.parse(packet, 12, 99));
        assertNull(RtpPcmuCodec.parse(packet, packet.length + 1, 99));
        byte[] wrong = packet.clone();
        wrong[0] = (byte) 0xc0; // Wrong RTP version.
        assertNull(RtpPcmuCodec.parse(wrong, wrong.length, 99));
        wrong = packet.clone();
        wrong[0] = (byte) 0x81; // Unsupported CSRC.
        assertNull(RtpPcmuCodec.parse(wrong, wrong.length, 99));
        wrong = packet.clone();
        wrong[1] = (byte) 8; // PCMA is not silently decoded as PCMU.
        assertNull(RtpPcmuCodec.parse(wrong, wrong.length, 99));
        wrong = packet.clone();
        wrong[0] = (byte) 0x90; // Header extension not accepted in this minimal transport.
        assertNull(RtpPcmuCodec.parse(wrong, wrong.length, 99));
    }

    @Test public void packetizationRejectsPartialOrMissingPcm() {
        assertThrows(IllegalArgumentException.class,
            () -> RtpPcmuCodec.packetize(null, 0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> RtpPcmuCodec.packetize(new byte[300], 0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> RtpPcmuCodec.packetize(new byte[321], 2, 1, 0, 1));
    }
}
