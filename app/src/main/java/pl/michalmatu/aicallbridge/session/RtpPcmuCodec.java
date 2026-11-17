package pl.michalmatu.aicallbridge.session;

import java.util.Arrays;

/**
 * RTP/AVP payload type 0 (G.711 mu-law, mono 8000 Hz), 20 ms frames.
 *
 * Strict, bounded framing for an app-owned VoIP media session. This is NOT SIP,
 * SRTP, a carrier bridge or a cellular call capture API. Remote transport must
 * authenticate its session before using any packet outside localhost.
 */
public final class RtpPcmuCodec {
    public static final int SAMPLE_RATE_HZ = 8000;
    public static final int FRAME_SAMPLES = 160;
    public static final int FRAME_PCM_BYTES = FRAME_SAMPLES * 2;
    public static final int RTP_HEADER_BYTES = 12;
    public static final int RTP_PAYLOAD_TYPE = 0;
    public static final int PACKET_BYTES = RTP_HEADER_BYTES + FRAME_SAMPLES;

    private RtpPcmuCodec() {}

    public record Frame(int sequence, long timestamp, byte[] pcm16) {}

    /** Convert a complete mono 20 ms PCM16LE frame into one RTP packet. */
    public static byte[] packetize(
        byte[] pcm16, int offset, int sequence, long timestamp, int ssrc
    ) {
        if (pcm16 == null || offset < 0 || offset > pcm16.length - FRAME_PCM_BYTES) {
            throw new IllegalArgumentException("invalid_pcm_frame");
        }
        byte[] packet = new byte[PACKET_BYTES];
        packet[0] = (byte) 0x80; // RTP version 2, no header extensions or CSRC.
        packet[1] = RTP_PAYLOAD_TYPE;
        write16(packet, 2, sequence);
        write32(packet, 4, timestamp);
        write32(packet, 8, Integer.toUnsignedLong(ssrc));
        for (int i = 0; i < FRAME_SAMPLES; i++) {
            int lo = pcm16[offset + i * 2] & 0xff;
            int hi = pcm16[offset + i * 2 + 1];
            packet[RTP_HEADER_BYTES + i] = encode((short) ((hi << 8) | lo));
        }
        return packet;
    }

    /**
     * Reject non-PCMU packets, extensions, wrong SSRC, and non-20ms payloads.
     * Sequence/replay validation belongs to the session, not this stateless codec.
     */
    public static Frame parse(byte[] packet, int length, int expectedSsrc) {
        if (packet == null || length != PACKET_BYTES
            || length > packet.length || (packet[0] & 0xff) != 0x80
            || (packet[1] & 0x7f) != RTP_PAYLOAD_TYPE
            || read32(packet, 8) != Integer.toUnsignedLong(expectedSsrc)) {
            return null;
        }
        int samples = length - RTP_HEADER_BYTES;
        byte[] pcm16 = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short sample = decode(packet[RTP_HEADER_BYTES + i]);
            pcm16[i * 2] = (byte) sample;
            pcm16[i * 2 + 1] = (byte) (sample >>> 8);
        }
        return new Frame(
            ((packet[2] & 0xff) << 8) | (packet[3] & 0xff),
            read32(packet, 4),
            pcm16
        );
    }

    public static short decode(byte pcmu) {
        int code = (~pcmu) & 0xff;
        int magnitude = (((code & 0x0f) << 3) + 0x84) << ((code >>> 4) & 0x07);
        int value = magnitude - 0x84;
        return (short) ((code & 0x80) != 0 ? -value : value);
    }

    public static byte encode(short sample) {
        int pcm = sample;
        int sign = pcm < 0 ? 0x80 : 0;
        if (pcm < 0) pcm = -pcm;
        pcm = Math.min(pcm, 32635) + 0x84;
        int exponent = 7;
        for (int mask = 0x4000; exponent > 0 && (pcm & mask) == 0; mask >>>= 1) {
            exponent--;
        }
        int mantissa = (pcm >>> (exponent + 3)) & 0x0f;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    private static void write16(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 8);
        out[offset + 1] = (byte) value;
    }

    private static void write32(byte[] out, int offset, long value) {
        for (int i = 3; i >= 0; i--) {
            out[offset + i] = (byte) value;
            value >>>= 8;
        }
    }

    private static long read32(byte[] input, int offset) {
        return ((long) (input[offset] & 0xff) << 24)
            | ((long) (input[offset + 1] & 0xff) << 16)
            | ((long) (input[offset + 2] & 0xff) << 8)
            | (long) (input[offset + 3] & 0xff);
    }
}
