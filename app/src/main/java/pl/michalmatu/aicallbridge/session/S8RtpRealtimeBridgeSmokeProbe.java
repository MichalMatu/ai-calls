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

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

/**
 * Android one-shot, off-call verification of the REAL production PCM boundary:
 * RTP/PCMU 8k <-> app PCM16 16k <-> Realtime PCM16 24k.
 *
 * Uses synthetic silence, app-owned loopback only, no microphone, network
 * provider, SIP account, phone call, or Shizuku. Does not release voice.
 */
public final class S8RtpRealtimeBridgeSmokeProbe {
    private static final int PEER_SSRC = 0x0a0b0c0d;
    private static final int APP_SSRC = 0x01020304;
    private static final PcmFormat REALTIME_FORMAT = new PcmFormat(24_000, 1, 16);

    public record Result(boolean sdp, boolean publicEndpointRejected,
                         boolean rx, boolean tx, boolean stop) {
        public boolean allPassed() {
            return sdp && publicEndpointRejected && rx && tx && stop;
        }
    }

    private S8RtpRealtimeBridgeSmokeProbe() {}

    public static Result run() throws IOException {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        Object exactIdentity = new Object();
        try (DatagramSocket peer = new DatagramSocket(new InetSocketAddress(loopback, 0));
             AppOwnedLoopbackRtpMediaPort rtp = new AppOwnedLoopbackRtpMediaPort(
                 exactIdentity, 0, negotiatedPeerPort(peer.getLocalPort()), PEER_SSRC, APP_SSRC
             );
             RtpPcm16kAdapterMediaPort pcm = new RtpPcm16kAdapterMediaPort(rtp);
             AppOwnedVoipCallMediaSessionBackend backend =
                 new AppOwnedVoipCallMediaSessionBackend(pcm, exactIdentity)) {

            peer.setSoTimeout(1200);
            CallMediaSessionCoordinator coordinator = new CallMediaSessionCoordinator(
                backend, (generation, heartbeat) -> () -> {},
                System::currentTimeMillis, ignored -> {}
            );
            try {
                long generation = coordinator.start(TelephonyPcmStreamFramer.SAMPLE_RATE_HZ);
                if (coordinator.snapshot().state() != CallMediaSessionState.ACTIVE) {
                    return new Result(false, false, false, false, false);
                }
                // The RTP peer port is selected from a validated localhost SDP.
                // Verify that the actual bound app-side port can likewise be advertised.
                String localSdp = S8LoopbackSdp.offer(rtp.boundLocalPort(), 29003L);
                boolean sdp = S8LoopbackSdp.acceptLoopbackAnswer(localSdp)
                    .peerRtpPort() == rtp.boundLocalPort();
                boolean publicEndpointRejected = false;
                try {
                    S8LoopbackSdp.acceptLoopbackAnswer(
                        localSdp.replace("c=IN IP4 127.0.0.1",
                            "c=IN IP4 198.51.100.1")
                    );
                } catch (IllegalArgumentException expected) {
                    publicEndpointRejected = true;
                }
                CallMediaEndpointLease endpoints = coordinator.activeEndpointLease(generation);
                CallRealtimePcmBridge bridge = new CallRealtimePcmBridge(endpoints);

                // RX: send one 20 ms PCMU frame from the loopback "remote".
                byte[] silence = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
                byte[] incoming = RtpPcmuCodec.packetize(silence, 0, 10, 1600, PEER_SSRC);
                peer.send(new DatagramPacket(
                    incoming, incoming.length, loopback, rtp.boundLocalPort()
                ));
                FutureTask<PcmFrame> rxJob = new FutureTask<>(
                    () -> bridge.readRealtimeInputFrame(System.nanoTime())
                );
                Thread rxThread = new Thread(rxJob, "s8-realtime-bridge-smoke-rx");
                rxThread.setDaemon(true);
                rxThread.start();

                boolean rx = false;
                try {
                    PcmFrame frame = rxJob.get(2, TimeUnit.SECONDS);
                    rx = frame != null && REALTIME_FORMAT.equals(frame.getFormat())
                        && frame.getData().length == 960
                        && Arrays.equals(frame.getData(), new byte[960])
                        && bridge.snapshot().downlinkFrames() == 1L;
                } catch (TimeoutException expired) {
                    rxJob.cancel(true);
                } catch (Exception failed) {
                    rxJob.cancel(true);
                }

                // TX: convert one 24k Realtime frame to the RTP 8k peer.
                boolean tx = false;
                try {
                    bridge.writeRealtimeOutputFrame(
                        new PcmFrame(REALTIME_FORMAT, new byte[960], System.nanoTime())
                    );
                    DatagramPacket packet = new DatagramPacket(new byte[256], 256);
                    peer.receive(packet);
                    RtpPcmuCodec.Frame result =
                        RtpPcmuCodec.parse(packet.getData(), packet.getLength(), APP_SSRC);
                    tx = result != null
                        && result.pcm16().length == RtpPcmuCodec.FRAME_PCM_BYTES
                        && Arrays.equals(result.pcm16(), silence)
                        && bridge.snapshot().uplinkFrames() == 1L;
                } catch (Exception failed) {
                    tx = false;
                }

                coordinator.takeOverNow();
                try {
                    rxThread.join(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                boolean stop = !rxThread.isAlive()
                    && coordinator.snapshot().state() == CallMediaSessionState.IDLE
                    && !backend.heartbeat(generation)
                    && rtp.boundLocalPort() == -1
                    && endpoints.downlink().read() == -1;
                return new Result(sdp, publicEndpointRejected, rx, tx, stop);
            } finally {
                coordinator.takeOverNow();
            }
        }
    }
    private static int negotiatedPeerPort(int sourcePort) {
        String peerOffer = S8LoopbackSdp.offer(sourcePort, 29002L);
        return S8LoopbackSdp.acceptLoopbackAnswer(peerOffer).peerRtpPort();
    }

}
