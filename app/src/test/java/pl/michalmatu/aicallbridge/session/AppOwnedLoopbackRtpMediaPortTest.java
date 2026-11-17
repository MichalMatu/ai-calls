package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Offline localhost-only media tests. No SIP account, ADB, microphone or telephone call. */
public final class AppOwnedLoopbackRtpMediaPortTest {
    private static final int RX_SSRC = 0x12121212;
    private static final int TX_SSRC = 0x34343434;
    private static final InetAddress LOOP = loopback();

    private static InetAddress loopback() {
        try {
            return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static DatagramSocket peer() throws IOException {
        DatagramSocket socket = new DatagramSocket(new InetSocketAddress(LOOP, 0));
        socket.setSoTimeout(1000);
        return socket;
    }

    private static AppOwnedLoopbackRtpMediaPort newPort(Object identity, DatagramSocket peer) {
        return new AppOwnedLoopbackRtpMediaPort(
            identity, 0, peer.getLocalPort(), RX_SSRC, TX_SSRC
        );
    }

    @Test public void pcmUplinkProducesRealRtpPacketOnLocalhost() throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            assertFalse(port.mediaHealthy(identity));
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            assertTrue(port.mediaHealthy(identity));
            assertTrue(port.boundLocalPort() >= 1024);
            byte[] silence = new byte[320];
            lease.uplink().write(silence);
            DatagramPacket response = new DatagramPacket(new byte[500], 500);
            remote.receive(response);
            assertEquals(RtpPcmuCodec.PACKET_BYTES, response.getLength());
            RtpPcmuCodec.Frame frame =
                RtpPcmuCodec.parse(response.getData(), response.getLength(), TX_SSRC);
            assertNotNull(frame);
            assertEquals(0, frame.sequence());
            assertEquals(0L, frame.timestamp());
            assertArrayEquals(silence, frame.pcm16());
            lease.close();
            assertFalse(port.mediaHealthy(identity));
            assertThrows(IOException.class, () -> lease.uplink().write(silence));
        }
    }

    @Test public void downlinkRejectsWrongSsrcAndReplayedSequence() throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            int portNumber = port.boundLocalPort();
            byte[] silence = new byte[320];
            send(remote, portNumber, RtpPcmuCodec.packetize(silence, 0, 41, 1000, 999));
            send(remote, portNumber, RtpPcmuCodec.packetize(silence, 0, 41, 1000, RX_SSRC));
            byte[] actual = new byte[320];
            assertEquals(320, lease.downlink().read(actual, 0, 320));
            assertArrayEquals(silence, actual);

            // Duplicate is dropped; the next valid RTP frame is delivered instead.
            byte[] duplicate = new byte[320];
            for (int i = 0; i < 160; i++) {
                duplicate[i * 2] = 0x10;
            }
            send(remote, portNumber, RtpPcmuCodec.packetize(duplicate, 0, 41, 1000, RX_SSRC));
            send(remote, portNumber, RtpPcmuCodec.packetize(silence, 0, 42, 1160, RX_SSRC));
            assertEquals(320, lease.downlink().read(actual, 0, 320));
            assertArrayEquals("replayed PCM was rejected", silence, actual);
        }
    }

    @Test public void malformedLengthsCannotBeTruncatedIntoValidPcm() throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            byte[] noisy = new byte[320];
            noisy[0] = 0x30;
            byte[] validLength = RtpPcmuCodec.packetize(noisy, 0, 1, 160, RX_SSRC);
            byte[] shortPacket = java.util.Arrays.copyOf(validLength, validLength.length - 1);
            byte[] oversized = java.util.Arrays.copyOf(validLength, validLength.length + 8);
            send(remote, port.boundLocalPort(), shortPacket);
            send(remote, port.boundLocalPort(), oversized);
            byte[] silence = new byte[320];
            send(remote, port.boundLocalPort(),
                RtpPcmuCodec.packetize(silence, 0, 1, 160, RX_SSRC));
            byte[] actual = new byte[320];
            assertEquals(320, lease.downlink().read(actual, 0, actual.length));
            assertArrayEquals("only the full, valid 20ms RTP is accepted", silence, actual);
        }
    }

    @Test public void stalledRtpReadFailsInsteadOfBlockingIndefinitely() throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            assertThrows(SocketTimeoutException.class,
                () -> lease.downlink().read(new byte[320], 0, 320));
        }
    }

    @Test public void missingAndLateRtpFramesDoNotReplayOldSpeechOrBlockRx()
        throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            int local = port.boundLocalPort();
            byte[] silence = new byte[320];
            byte[] speech = new byte[320];
            for (int i = 0; i < 160; i++) {
                short sample = 6_000;
                speech[i * 2] = (byte) sample;
                speech[i * 2 + 1] = (byte) (sample >>> 8);
            }
            send(remote, local, RtpPcmuCodec.packetize(silence, 0, 10, 1600, RX_SSRC));
            // Packet 11 is lost. The port must still accept the next frame.
            send(remote, local, RtpPcmuCodec.packetize(speech, 0, 12, 1920, RX_SSRC));
            // It arrives late and must never replay stale voice.
            send(remote, local, RtpPcmuCodec.packetize(speech, 0, 11, 1760, RX_SSRC));
            send(remote, local, RtpPcmuCodec.packetize(silence, 0, 13, 2080, RX_SSRC));
            byte[] actual = new byte[320];
            assertEquals(320, lease.downlink().read(actual, 0, 320));
            assertArrayEquals(silence, actual);
            assertEquals(320, lease.downlink().read(actual, 0, 320));
            assertArrayEquals("missing frame 11 should be silence, not sped-up speech",
                silence, actual);
            assertEquals(320, lease.downlink().read(actual, 0, 320));
            assertFalse("frame 12 should contain live speech",
                java.util.Arrays.equals(silence, actual));
            assertEquals(320, lease.downlink().read(actual, 0, 320));
            assertArrayEquals("late packet 11 must be ignored", silence, actual);
        }
    }

    @Test public void upToTwoMissingFramesAreSilencedButLargeGapIsNotExpanded()
        throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            int local = port.boundLocalPort();
            byte[] silence = new byte[320];
            byte[] voice = new byte[320];
            for (int index = 0; index < 160; index++) {
                voice[index * 2] = 0x3f;
                voice[index * 2 + 1] = 0x11;
            }
            send(remote, local, RtpPcmuCodec.packetize(silence, 0, 20, 3200, RX_SSRC));
            send(remote, local, RtpPcmuCodec.packetize(voice, 0, 23, 3680, RX_SSRC));
            byte[] pcm = new byte[320];
            assertEquals(320, lease.downlink().read(pcm));
            assertArrayEquals(silence, pcm);
            assertEquals(320, lease.downlink().read(pcm));
            assertArrayEquals("missing 21", silence, pcm);
            assertEquals(320, lease.downlink().read(pcm));
            assertArrayEquals("missing 22", silence, pcm);
            assertEquals(320, lease.downlink().read(pcm));
            assertFalse("live frame 23", java.util.Arrays.equals(silence, pcm));
            // Never materialize hundreds of synthetic frames after a long outage.
            send(remote, local, RtpPcmuCodec.packetize(voice, 0, 1000, 160000, RX_SSRC));
            assertEquals(320, lease.downlink().read(pcm));
            assertFalse("large jump resumes with one live frame",
                java.util.Arrays.equals(silence, pcm));
        }
    }

    @Test public void stopInterruptsBlockedReadAndDropsPartialTx() throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            lease.uplink().write(new byte[100]);
            CountDownLatch started = new CountDownLatch(1);
            FutureTask<Integer> reader = new FutureTask<>(() -> {
                started.countDown();
                return lease.downlink().read(new byte[320], 0, 320);
            });
            Thread thread = new Thread(reader, "s8-offcall-rtp-reader");
            thread.setDaemon(true);
            thread.start();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            lease.close();
            assertEquals(-1, (int) reader.get(2, TimeUnit.SECONDS));
            assertEquals(-1, port.boundLocalPort());
            assertFalse(port.mediaHealthy(identity));
            remote.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class,
                () -> remote.receive(new DatagramPacket(new byte[500], 500)));
        }
    }

    @Test public void exactIdentityAndSingleUseFailClosed() throws Exception {
        Object identity = new Object();
        try (DatagramSocket remote = peer();
             AppOwnedLoopbackRtpMediaPort port = newPort(identity, remote)) {
            assertThrows(IllegalArgumentException.class,
                () -> port.open(new Object(), 1, 8000));
            assertThrows(IllegalArgumentException.class,
                () -> port.open(identity, 1, 16000));
            CallMediaEndpointLease lease = port.open(identity, 1, 8000);
            assertThrows(IllegalStateException.class, () -> port.open(identity, 2, 8000));
            lease.close();
            assertThrows(IllegalStateException.class, () -> port.open(identity, 2, 8000));
        }
    }

    private static void send(DatagramSocket remote, int port, byte[] packet) throws IOException {
        remote.send(new DatagramPacket(packet, packet.length, LOOP, port));
    }
}
