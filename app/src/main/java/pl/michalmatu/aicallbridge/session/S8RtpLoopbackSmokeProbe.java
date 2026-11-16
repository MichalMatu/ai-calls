package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One-shot offline RTP smoke; only synthetic silence on 127.0.0.1.
 * Never touches the modem, microphone, Telecom, SIP credentials or Shizuku.
 */
public final class S8RtpLoopbackSmokeProbe {
    private static final int PEER_SSRC = 0x11223344;
    private static final int APP_SSRC = 0x55667788;

    public record Result(boolean tx, boolean rx, boolean stop) {
        public boolean allPassed() { return tx && rx && stop; }
    }

    private S8RtpLoopbackSmokeProbe() {}

    public static Result run() throws IOException {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        Object identity = new Object();
        try (DatagramSocket peer = new DatagramSocket(new InetSocketAddress(loopback, 0));
             AppOwnedLoopbackRtpMediaPort port = new AppOwnedLoopbackRtpMediaPort(
                 identity, 0, peer.getLocalPort(), PEER_SSRC, APP_SSRC
             );
             AppOwnedVoipCallMediaSessionBackend backend =
                 new AppOwnedVoipCallMediaSessionBackend(port, identity)) {
            peer.setSoTimeout(1200);
            CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
                backend, (generation, heartbeat) -> () -> {},
                System::currentTimeMillis, unused -> {}
            );
            try {
                long generation = coordinator.start(RtpPcmuCodec.SAMPLE_RATE_HZ);
                if (coordinator.snapshot().state() != CallMediaSessionState.ACTIVE) {
                    return new Result(false, false, false);
                }
                CallMediaEndpointLease endpoint = coordinator.activeEndpointLease(generation);
                byte[] silence = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
                endpoint.uplink().write(silence);
                DatagramPacket packet = new DatagramPacket(new byte[256], 256);
                peer.receive(packet);
                RtpPcmuCodec.Frame outbound =
                    RtpPcmuCodec.parse(packet.getData(), packet.getLength(), APP_SSRC);
                boolean tx = outbound != null && Arrays.equals(silence, outbound.pcm16());

                byte[] incoming = RtpPcmuCodec.packetize(silence, 0, 5, 800, PEER_SSRC);
                peer.send(new DatagramPacket(
                    incoming, incoming.length, loopback, port.boundLocalPort()
                ));
                byte[] read = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
                FutureTask<Integer> receiver = new FutureTask<>(
                    () -> endpoint.downlink().read(read, 0, read.length)
                );
                Thread rxThread = new Thread(receiver, "s8-loopback-smoke-rx");
                rxThread.setDaemon(true);
                rxThread.start();
                boolean rx = false;
                try {
                    int received = receiver.get(2, TimeUnit.SECONDS);
                    rx = received == read.length && Arrays.equals(silence, read);
                } catch (TimeoutException expired) {
                    // The media reader normally waits for remote RTP indefinitely.
                    // An off-call QA probe must never hold the Activity indefinitely.
                    receiver.cancel(true);
                } catch (Exception failed) {
                    receiver.cancel(true);
                }
                coordinator.takeOverNow(); // Closing socket also unblocks timed-out RX.
                try {
                    rxThread.join(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                boolean stop = !rxThread.isAlive()
                    && coordinator.snapshot().state() == CallMediaSessionState.IDLE
                    && !backend.heartbeat(generation)
                    && port.boundLocalPort() == -1
                    && endpoint.downlink().read(read, 0, read.length) == -1;
                return new Result(tx, rx, stop);
            } finally {
                coordinator.takeOverNow();
            }
        }
    }
}
