package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.Test;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

public final class CallRealtimePcmBridgeTest {
    private static final PcmFormat REALTIME = new PcmFormat(24_000, 1, 16);

    @Test
    public void convertsDownlinkAndUplinkAcrossTheExplicitPcmBoundary() throws Exception {
        byte[] telephonyInput = constantPcm(320, (short) 1234);
        FakeLease lease = new FakeLease(telephonyInput);
        CallRealtimePcmBridge bridge = new CallRealtimePcmBridge(lease);

        PcmFrame realtimeInput = bridge.readRealtimeInputFrame(111L);

        assertEquals(REALTIME, realtimeInput.getFormat());
        assertEquals(480 * 2, realtimeInput.getData().length);
        assertEquals(111L, realtimeInput.getMonotonicTimestampNs());

        byte[] realtimeOutput = constantPcm(480, (short) -2345);
        bridge.writeRealtimeOutputFrame(new PcmFrame(REALTIME, realtimeOutput, 222L));

        assertArrayEquals(constantPcm(320, (short) -2345), lease.uplink.toByteArray());
        CallRealtimePcmBridgeSnapshot snapshot = bridge.snapshot();
        assertEquals(640L, snapshot.downlinkBytes());
        assertEquals(1L, snapshot.downlinkFrames());
        assertEquals(640L, snapshot.uplinkBytes());
        assertEquals(1L, snapshot.uplinkFrames());
        assertNull(snapshot.terminalReason());
    }

    @Test
    public void cleanDownlinkEofBecomesTerminalAndBlocksFurtherIo() throws Exception {
        FakeLease lease = new FakeLease(new byte[0]);
        CallRealtimePcmBridge bridge = new CallRealtimePcmBridge(lease);

        assertNull(bridge.readRealtimeInputFrame(1L));
        assertEquals("downlink_eof", bridge.snapshot().terminalReason());
        assertThrows(IllegalStateException.class, () -> bridge.readRealtimeInputFrame(2L));
        assertThrows(
            IllegalStateException.class,
            () -> bridge.writeRealtimeOutputFrame(
                new PcmFrame(REALTIME, constantPcm(480, (short) 0), 3L)
            )
        );
    }

    @Test
    public void partialDownlinkFrameRecordsTerminalFailure() {
        byte[] partial = new byte[TelephonyPcmStreamFramer.FRAME_BYTES_20_MS - 2];
        FakeLease lease = new FakeLease(partial);
        CallRealtimePcmBridge bridge = new CallRealtimePcmBridge(lease);

        assertThrows(Exception.class, () -> bridge.readRealtimeInputFrame(1L));

        String reason = bridge.snapshot().terminalReason();
        assertTrue(reason.startsWith("downlink_failed:"));
        assertTrue(reason.contains("EOFException"));
    }

    @Test
    public void malformedRealtimeOutputRecordsTerminalFailureWithoutWritingBytes() {
        FakeLease lease = new FakeLease(new byte[TelephonyPcmStreamFramer.FRAME_BYTES_20_MS]);
        CallRealtimePcmBridge bridge = new CallRealtimePcmBridge(lease);

        assertThrows(
            IllegalArgumentException.class,
            () -> bridge.writeRealtimeOutputFrame(
                new PcmFrame(new PcmFormat(16_000, 1, 16), new byte[] {0, 0}, 1L)
            )
        );

        assertEquals(0, lease.uplink.size());
        assertTrue(bridge.snapshot().terminalReason().startsWith("uplink_failed:"));
    }

    private static byte[] constantPcm(int samples, short value) {
        byte[] data = new byte[samples * 2];
        int low = value & 0xff;
        int high = (value >>> 8) & 0xff;
        for (int i = 0; i < samples; i++) {
            data[i * 2] = (byte) low;
            data[i * 2 + 1] = (byte) high;
        }
        return data;
    }

    private static final class FakeLease implements CallMediaEndpointLease {
        final InputStream downlink;
        final ByteArrayOutputStream uplink = new ByteArrayOutputStream();

        FakeLease(byte[] downlinkBytes) {
            downlink = new ByteArrayInputStream(Arrays.copyOf(downlinkBytes, downlinkBytes.length));
        }

        @Override
        public InputStream downlink() {
            return downlink;
        }

        @Override
        public OutputStream uplink() {
            return uplink;
        }

        @Override
        public void close() {}
    }
}
