package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import pl.michalmatu.aicallbridge.audio.PcmFormat;
import pl.michalmatu.aicallbridge.audio.PcmFrame;

/**
 * User-triggered, fully app-owned six-phase media exercise WITHOUT Shizuku,
 * ADB, GSM, SIP registration, account, mic, network carrier or human audio.
 *
 * Actual production media path:
 * localhost RTP/PCMU 8k -> app PCM 16k -> Realtime PCM 24k -> RTP/PCMU 8k.
 * Six phases are SYNTHETIC audio patterns, NOT six spoken AI turns. The lab
 * does not prove STT, inference, TTS, carrier reachability, PSTN or SRTP.
 *
 * STOP can close the socket and session from another thread while RX blocks.
 * One instance is one session. Never run on Android main thread.
 */
public final class S8VoipSixTurnLab implements AutoCloseable {
    private static final int PHASES = 6;
    private static final int FRAMES_PER_PHASE = 12;
    private static final int FRAMES = PHASES * FRAMES_PER_PHASE;
    private static final int PEER_SSRC = 0x4131a230;
    private static final int APP_SSRC = 0x5135c320;
    private static final PcmFormat REALTIME = new PcmFormat(24_000, 1, 16);

    public record Result(
        int phasesCompleted, int rxFrames, int txFrames,
        boolean sdp, boolean publicEndpointRejected, boolean stopped
    ) {
        public boolean passed() {
            return phasesCompleted == PHASES && rxFrames == FRAMES
                && txFrames == FRAMES && sdp && publicEndpointRejected && stopped;
        }
    }

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<DatagramSocket> peerSocket = new AtomicReference<>();
    private final AtomicReference<AppOwnedLoopbackRtpMediaPort> portRef = new AtomicReference<>();
    private final AtomicReference<CallMediaSessionCoordinator> ownerRef = new AtomicReference<>();

    /**
     * Executes one bounded, non-speech lab. The activity or test owns and
     * closes this object when it needs immediate STOP or on lifecycle loss.
     */
    public Result run() throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("s8_lab_single_use");
        }
        if (closed.get()) return new Result(0, 0, 0, false, false, true);

        int phases = 0;
        int rx = 0;
        int tx = 0;
        boolean sdp = false;
        boolean publicRejected = false;

        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        try (DatagramSocket peer = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            peerSocket.set(peer);
            peer.setSoTimeout(1500);
            if (closed.get()) return new Result(0, 0, 0, false, false, true);

            int selectedPeerPort = S8LoopbackSdp.acceptLoopbackAnswer(
                S8LoopbackSdp.offer(peer.getLocalPort(), 70031L)
            ).peerRtpPort();
            Object exactIdentity = new Object();
            try (AppOwnedLoopbackRtpMediaPort port = new AppOwnedLoopbackRtpMediaPort(
                     exactIdentity, 0, selectedPeerPort, PEER_SSRC, APP_SSRC);
                 RtpPcm16kAdapterMediaPort pcm = new RtpPcm16kAdapterMediaPort(port);
                 AppOwnedVoipCallMediaSessionBackend backend =
                     new AppOwnedVoipCallMediaSessionBackend(pcm, exactIdentity)) {
                portRef.set(port);
                CallMediaSessionCoordinator owner = new CallMediaSessionCoordinator(
                    backend, (generation, heartbeat) -> () -> {},
                    System::currentTimeMillis, ignored -> {}
                );
                ownerRef.set(owner);
                try {
                    if (closed.get()) return new Result(0, 0, 0, false, false, true);
                    long generation = owner.start(TelephonyPcmStreamFramer.SAMPLE_RATE_HZ);
                    if (owner.snapshot().state() != CallMediaSessionState.ACTIVE) {
                        throw new IOException("s8_lab_media_not_active");
                    }
                    int localPort = port.boundLocalPort();
                    String localOffer = S8LoopbackSdp.offer(localPort, 70032L);
                    sdp = S8LoopbackSdp.acceptLoopbackAnswer(localOffer)
                        .peerRtpPort() == localPort;
                    try {
                        S8LoopbackSdp.acceptLoopbackAnswer(localOffer.replace(
                            "c=IN IP4 127.0.0.1", "c=IN IP4 203.0.113.99"
                        ));
                    } catch (IllegalArgumentException expected) {
                        publicRejected = true;
                    }
                    if (!sdp || !publicRejected) throw new IOException("s8_lab_sdp_rejected");
                    CallRealtimePcmBridge bridge = new CallRealtimePcmBridge(
                        owner.activeEndpointLease(generation)
                    );

                    for (int phase = 0; phase < PHASES && !closed.get(); phase++) {
                        for (int frameNumber = 0; frameNumber < FRAMES_PER_PHASE; frameNumber++) {
                            if (closed.get()) break;
                            int index = phase * FRAMES_PER_PHASE + frameNumber;
                            byte[] narrow = syntheticVoiceBandFrame(phase, frameNumber);
                            byte[] incoming = RtpPcmuCodec.packetize(
                                narrow, 0, index, index * RtpPcmuCodec.FRAME_SAMPLES,
                                PEER_SSRC
                            );
                            peer.send(new DatagramPacket(
                                incoming, incoming.length, loopback, localPort
                            ));
                            PcmFrame pcm24 = bridge.readRealtimeInputFrame(System.nanoTime());
                            if (pcm24 == null || !REALTIME.equals(pcm24.getFormat())
                                || pcm24.getData().length != 960
                                || allZero(pcm24.getData())) {
                                throw new IOException("s8_lab_rx_invalid");
                            }
                            rx++;
                            bridge.writeRealtimeOutputFrame(pcm24);
                            DatagramPacket delivered = new DatagramPacket(new byte[256], 256);
                            peer.receive(delivered);
                            RtpPcmuCodec.Frame decoded = RtpPcmuCodec.parse(
                                delivered.getData(), delivered.getLength(), APP_SSRC
                            );
                            if (decoded == null || decoded.sequence() != index
                                || decoded.pcm16().length != RtpPcmuCodec.FRAME_PCM_BYTES
                                || allZero(decoded.pcm16())) {
                                throw new IOException("s8_lab_tx_invalid");
                            }
                            tx++;
                        }
                        if (!closed.get()) phases++;
                    }
                } finally {
                    owner.takeOverNow();
                }
            }
        } catch (SocketException error) {
            if (!closed.get()) throw error;
        } catch (IOException error) {
            if (!closed.get()) throw error;
        } finally {
            close();
        }
        return new Result(phases, rx, tx, sdp, publicRejected,
            peerSocket.get() == null || peerSocket.get().isClosed());
    }

    /** Cancels RX/TX now; peer socket close unblocks receive. Idempotent. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        CallMediaSessionCoordinator owner = ownerRef.getAndSet(null);
        if (owner != null) owner.takeOverNow();
        AppOwnedLoopbackRtpMediaPort port = portRef.getAndSet(null);
        if (port != null) port.close();
        DatagramSocket peer = peerSocket.getAndSet(null);
        if (peer != null) peer.close();
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) if (value != 0) return false;
        return true;
    }

    private static byte[] syntheticVoiceBandFrame(int phase, int frameNumber) {
        byte[] frame = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
        int amplitude = 1200 + phase * 350;
        for (int i = 0; i < RtpPcmuCodec.FRAME_SAMPLES; i++) {
            int polarity = ((i / 8 + frameNumber) & 1) == 0 ? 1 : -1;
            short value = (short) (polarity * amplitude);
            frame[i * 2] = (byte) value;
            frame[i * 2 + 1] = (byte) (value >>> 8);
        }
        return frame;
    }
}
