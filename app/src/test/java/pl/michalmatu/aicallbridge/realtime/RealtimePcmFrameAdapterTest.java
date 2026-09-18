package pl.michalmatu.aicallbridge.realtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

public final class RealtimePcmFrameAdapterTest {
    private static final PcmFormat TELEPHONY = new PcmFormat(16_000, 1, 16);
    private static final PcmFormat REALTIME = new PcmFormat(24_000, 1, 16);

    private final RealtimePcmFrameAdapter adapter = new RealtimePcmFrameAdapter();

    @Test
    public void convertsTwentyMsTelephonyFrameToRealtimePcm24k() {
        short[] input = ramp(320, -12_000, 75);
        PcmFrame source = frame(TELEPHONY, input, 123_456_789L);

        PcmFrame converted = adapter.toRealtime(source);

        assertEquals(REALTIME, converted.getFormat());
        assertEquals(480 * 2, converted.getData().length);
        assertEquals(source.getMonotonicTimestampNs(), converted.getMonotonicTimestampNs());
        assertNotSame(source.getData(), converted.getData());
        assertEquals(input[0], sampleAt(converted.getData(), 0));
        assertEquals(input[input.length - 1], sampleAt(converted.getData(), 479));
    }

    @Test
    public void convertsTwentyMsRealtimeFrameToTelephonyPcm16k() {
        short[] input = ramp(480, -8_000, 40);
        PcmFrame source = frame(REALTIME, input, 987_654_321L);

        PcmFrame converted = adapter.toTelephony(source);

        assertEquals(TELEPHONY, converted.getFormat());
        assertEquals(320 * 2, converted.getData().length);
        assertEquals(source.getMonotonicTimestampNs(), converted.getMonotonicTimestampNs());
        assertEquals(input[0], sampleAt(converted.getData(), 0));
        assertEquals(input[input.length - 1], sampleAt(converted.getData(), 319));
    }

    @Test
    public void constantSignalRemainsConstantBothDirections() {
        short[] telephonySamples = constant(320, (short) 1_234);
        short[] realtimeSamples = constant(480, (short) -2_345);

        assertArrayEquals(
            constant(480, (short) 1_234),
            decode(adapter.toRealtime(frame(TELEPHONY, telephonySamples, 1L)).getData())
        );
        assertArrayEquals(
            constant(320, (short) -2_345),
            decode(adapter.toTelephony(frame(REALTIME, realtimeSamples, 2L)).getData())
        );
    }

    @Test
    public void rejectsUnexpectedFormatsAndMalformedPcm() {
        assertThrows(
            IllegalArgumentException.class,
            () -> adapter.toRealtime(frame(new PcmFormat(8_000, 1, 16), constant(160, (short) 0), 1L))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> adapter.toRealtime(frame(new PcmFormat(16_000, 2, 16), constant(320, (short) 0), 1L))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> adapter.toTelephony(new PcmFrame(REALTIME, new byte[] {1, 2, 3}, 1L))
        );
    }

    @Test
    public void sourceBytesAreNotMutated() {
        PcmFrame source = frame(TELEPHONY, ramp(320, -2_000, 13), 5L);
        byte[] before = source.getData().clone();

        adapter.toRealtime(source);

        assertArrayEquals(before, source.getData());
    }

    private static PcmFrame frame(PcmFormat format, short[] samples, long timestampNs) {
        return new PcmFrame(format, encode(samples), timestampNs);
    }

    private static short[] ramp(int count, int start, int step) {
        short[] result = new short[count];
        for (int i = 0; i < count; i++) {
            result[i] = (short) (start + i * step);
        }
        return result;
    }

    private static short[] constant(int count, short value) {
        short[] result = new short[count];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static byte[] encode(short[] samples) {
        byte[] result = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int value = samples[i] & 0xffff;
            result[i * 2] = (byte) value;
            result[i * 2 + 1] = (byte) (value >>> 8);
        }
        return result;
    }

    private static short[] decode(byte[] pcm) {
        short[] result = new short[pcm.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = sampleAt(pcm, i);
        }
        return result;
    }

    private static short sampleAt(byte[] pcm, int index) {
        int offset = index * 2;
        int low = pcm[offset] & 0xff;
        int high = pcm[offset + 1] << 8;
        return (short) (high | low);
    }
}
