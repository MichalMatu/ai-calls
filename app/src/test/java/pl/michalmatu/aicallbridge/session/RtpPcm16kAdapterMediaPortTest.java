package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

import org.junit.Test;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

public final class RtpPcm16kAdapterMediaPortTest {
    private static final int PEER_SSRC = 0x11111111;
    private static final int APP_SSRC = 0x22222222;
    private static final PcmFormat REALTIME = new PcmFormat(24000, 1, 16);

    @Test public void preservesSilenceAndVoicePolarityAcrossBothResamplingStages() {
        byte[] narrow = constant(160, (short) -1200);
        byte[] wide = new byte[640];
        RtpPcm16kAdapterMediaPort.expand8kTo16k(narrow, wide);
        assertArrayEquals(constant(320, (short) -1200), wide);

        byte[] reduced = new byte[320];
        RtpPcm16kAdapterMediaPort.reduce16kTo8k(wide, reduced);
        assertArrayEquals(narrow, reduced);
        assertThrows(IllegalArgumentException.class,
            () -> RtpPcm16kAdapterMediaPort.reduce16kTo8k(new byte[320], reduced));
        assertThrows(IllegalArgumentException.class,
            () -> RtpPcm16kAdapterMediaPort.expand8kTo16k(narrow, new byte[320]));
    }

    @Test public void downlinkAndFragmentedUplinkProduceExactFrameSizes() throws Exception {
        Object identity = new Object();
        FakePort delegate = new FakePort(identity);
        RtpPcm16kAdapterMediaPort adapter = new RtpPcm16kAdapterMediaPort(delegate);
        assertThrows(IllegalArgumentException.class, () -> adapter.open(identity, 1, 8000));
        assertEquals(0, delegate.opens);

        CallMediaEndpointLease endpoint = adapter.open(identity, 1, 16000);
        assertEquals(1, delegate.opens);
        assertEquals(8000, delegate.rate);

        byte[] actual = new byte[640];
        assertEquals(70, endpoint.downlink().read(actual, 0, 70));
        assertEquals(570, endpoint.downlink().read(actual, 70, 570));
        assertArrayEquals(constant(320, (short) 4096), actual);
        assertEquals(-1, endpoint.downlink().read(actual));

        byte[] outgoing = constant(320, (short) -8192);
        endpoint.uplink().write(outgoing, 0, 100);
        assertEquals(0, delegate.lease.out.size());
        endpoint.uplink().write(outgoing, 100, 540);
        assertArrayEquals(constant(160, (short) -8192), delegate.lease.out.toByteArray());

        // STOP discards an incomplete second frame and closes the underlying lease once.
        endpoint.uplink().write(new byte[10]);
        endpoint.close();
        endpoint.close();
        assertEquals(1, delegate.lease.closes);
        assertEquals(320, delegate.lease.out.size());
        assertEquals(-1, endpoint.downlink().read(actual));
        assertThrows(IOException.class, () -> endpoint.uplink().write(new byte[640]));
    }

    @Test public void physicalLocalhostRtpFeedsFrozen24kRealtimeBridgeAndReturnsRtp() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        Object identity = new Object();
        try (DatagramSocket peer = new DatagramSocket(new InetSocketAddress(loopback, 0));
             AppOwnedLoopbackRtpMediaPort raw = new AppOwnedLoopbackRtpMediaPort(
                 identity, 0, peer.getLocalPort(), PEER_SSRC, APP_SSRC);
             RtpPcm16kAdapterMediaPort port = new RtpPcm16kAdapterMediaPort(raw);
             AppOwnedVoipCallMediaSessionBackend backend =
                 new AppOwnedVoipCallMediaSessionBackend(port, identity)) {

            peer.setSoTimeout(1500);
            CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
                backend, (generation, heartbeat) -> () -> {}, () -> 100L, unused -> {}
            );
            try {
                long generation = coordinator.start(16000);
                assertEquals(CallMediaSessionState.ACTIVE, coordinator.snapshot().state());
                CallRealtimePcmBridge bridge =
                    new CallRealtimePcmBridge(coordinator.activeEndpointLease(generation));

                byte[] voiceband = constant(160, (short) 1600);
                byte[] inbound = RtpPcmuCodec.packetize(voiceband, 0, 10, 1600, PEER_SSRC);
                peer.send(new DatagramPacket(inbound, inbound.length, loopback, raw.boundLocalPort()));
                PcmFrame realtime = bridge.readRealtimeInputFrame(90L);
                assertEquals(REALTIME, realtime.getFormat());
                assertEquals(960, realtime.getData().length);
                assertEquals(90L, realtime.getMonotonicTimestampNs());
                assertTrue(read(realtime.getData(), 100) > 1000);

                bridge.writeRealtimeOutputFrame(
                    new PcmFrame(REALTIME, constant(480, (short) -1600), 91L)
                );
                DatagramPacket datagram = new DatagramPacket(new byte[256], 256);
                peer.receive(datagram);
                RtpPcmuCodec.Frame result = RtpPcmuCodec.parse(
                    datagram.getData(), datagram.getLength(), APP_SSRC
                );
                assertNotNull(result);
                assertEquals(320, result.pcm16().length);
                assertTrue(read(result.pcm16(), 20) < -1000);
                assertEquals(1L, bridge.snapshot().downlinkFrames());
                assertEquals(1L, bridge.snapshot().uplinkFrames());

                coordinator.takeOverNow();
                assertEquals(CallMediaSessionState.IDLE, coordinator.snapshot().state());
                assertFalse(backend.heartbeat(generation));
                assertEquals(-1, raw.boundLocalPort());
                assertThrows(IllegalStateException.class,
                    () -> coordinator.activeEndpointLease(generation));
            } finally {
                coordinator.takeOverNow();
            }
        }
    }

    private static byte[] constant(int samples, short value) {
        byte[] bytes = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            bytes[i * 2] = (byte) value;
            bytes[i * 2 + 1] = (byte) (value >>> 8);
        }
        return bytes;
    }

    private static short read(byte[] data, int i) {
        return (short) (((data[i * 2 + 1] & 0xff) << 8) | (data[i * 2] & 0xff));
    }

    private static final class FakePort implements AppOwnedVoipCallMediaSessionBackend.MediaPort {
        final Object identity;
        final FakeLease lease = new FakeLease();
        int opens;
        int rate;

        FakePort(Object identity) { this.identity = identity; }
        @Override public Object activeIdentity() { return identity; }
        @Override public boolean mediaHealthy(Object exact) { return exact == identity; }
        @Override public CallMediaEndpointLease open(Object exact, long generation, int sampleRateHz) {
            assertSame(identity, exact);
            opens++;
            rate = sampleRateHz;
            return lease;
        }
    }

    private static final class FakeLease implements CallMediaEndpointLease {
        final InputStream in = new ByteArrayInputStream(constant(160, (short) 4096));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int closes;
        @Override public InputStream downlink() { return in; }
        @Override public OutputStream uplink() { return out; }
        @Override public void close() { closes++; }
    }
}
