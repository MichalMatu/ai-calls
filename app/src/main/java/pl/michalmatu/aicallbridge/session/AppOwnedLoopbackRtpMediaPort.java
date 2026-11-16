package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Real bidirectional RTP/PCMU <-> PCM16LE port, loopback-only for S8 lab.
 *
 * Owns a single app-identified VoIP session, not a cellular Call. No SIP
 * registration or public network RTP: bare RTP has no encryption/authentication.
 * A separate SIP/SDP + SRTP implementation is needed for any remote carrier.
 */
public final class AppOwnedLoopbackRtpMediaPort
    implements AppOwnedVoipCallMediaSessionBackend.MediaPort, AutoCloseable {

    private static final long RTP_FRAME_NS = 20_000_000L;
    private final Object lock = new Object();
    private final Object exactIdentity;
    private final int localPort;
    private final int remotePort;
    private final int expectedRemoteSsrc;
    private final int outboundSsrc;
    private SessionLease lease;
    private boolean opened;
    private boolean closed;

    /**
     * localPort=0 binds an ephemeral localhost port; call boundLocalPort() after open.
     * remotePort must be the already-negotiated localhost peer's RTP port.
     */
    public AppOwnedLoopbackRtpMediaPort(
        Object exactIdentity, int localPort, int remotePort,
        int expectedRemoteSsrc, int outboundSsrc
    ) {
        this.exactIdentity = Objects.requireNonNull(exactIdentity, "exactIdentity");
        if (localPort < 0 || localPort > 65535 || remotePort < 1024 || remotePort > 65535) {
            throw new IllegalArgumentException("invalid_loopback_rtp_ports");
        }
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.expectedRemoteSsrc = expectedRemoteSsrc;
        this.outboundSsrc = outboundSsrc;
    }

    @Override
    public Object activeIdentity() {
        synchronized (lock) {
            return closed ? null : exactIdentity;
        }
    }

    @Override
    public boolean mediaHealthy(Object identity) {
        synchronized (lock) {
            return !closed && identity == exactIdentity && lease != null && !lease.isClosed();
        }
    }

    @Override
    public CallMediaEndpointLease open(Object identity, long generation, int sampleRateHz) {
        if (identity != exactIdentity || generation <= 0
            || sampleRateHz != RtpPcmuCodec.SAMPLE_RATE_HZ) {
            throw new IllegalArgumentException("invalid_exact_voip_media_request");
        }
        synchronized (lock) {
            if (closed || opened) throw new IllegalStateException("voip_media_session_unavailable");
            // Single-use: a completed or aborted generation cannot reopen this RTP port.
            opened = true;
        }
        DatagramSocket socket = null;
        try {
            InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            socket = new DatagramSocket(null);
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(loopback, localPort));
            socket.connect(new InetSocketAddress(loopback, remotePort));
            socket.setSoTimeout(100);
            SessionLease candidate = new SessionLease(socket, expectedRemoteSsrc, outboundSsrc);
            synchronized (lock) {
                if (!closed) {
                    lease = candidate;
                    return candidate;
                }
            }
            candidate.close();
            throw new IllegalStateException("voip_media_closed_during_open");
        } catch (IOException | RuntimeException error) {
            if (socket != null) socket.close();
            throw new IllegalStateException("loopback_rtp_open_failed", error);
        }
    }

    public int boundLocalPort() {
        synchronized (lock) {
            return lease == null || lease.isClosed() ? -1 : lease.socket.getLocalPort();
        }
    }

    @Override
    public void close() {
        SessionLease candidate;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            candidate = lease;
            lease = null;
        }
        if (candidate != null) candidate.close();
    }

    private static final class SessionLease implements CallMediaEndpointLease {
        final DatagramSocket socket;
        private final int expectedRemoteSsrc;
        private final int outboundSsrc;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Object rxLock = new Object();
        private final Object txLock = new Object();
        private final byte[] receivePacket = new byte[RtpPcmuCodec.PACKET_BYTES];
        private byte[] pendingRx = new byte[0];
        private int pendingRxOffset;
        private int lastSequence = -1;
        private final byte[] pendingTx = new byte[RtpPcmuCodec.FRAME_PCM_BYTES];
        private int pendingTxCount;
        private int outboundSequence;
        private long outboundTimestamp;
        private long nextSendAtNs;

        SessionLease(DatagramSocket socket, int expectedRemoteSsrc, int outboundSsrc) {
            this.socket = socket;
            this.expectedRemoteSsrc = expectedRemoteSsrc;
            this.outboundSsrc = outboundSsrc;
        }

        boolean isClosed() { return closed.get(); }

        private final InputStream incoming = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                if (length == 0) return 0;
                synchronized (rxLock) {
                    while (!closed.get()) {
                        if (pendingRxOffset < pendingRx.length) {
                            int count = Math.min(length, pendingRx.length - pendingRxOffset);
                            System.arraycopy(pendingRx, pendingRxOffset, bytes, offset, count);
                            pendingRxOffset += count;
                            if (pendingRxOffset == pendingRx.length) {
                                Arrays.fill(pendingRx, (byte) 0);
                                pendingRx = new byte[0];
                                pendingRxOffset = 0;
                            }
                            return count;
                        }
                        DatagramPacket datagram =
                            new DatagramPacket(receivePacket, receivePacket.length);
                        try {
                            socket.receive(datagram);
                        } catch (SocketTimeoutException ignored) {
                            continue; // STOP remains independent of remote media.
                        } catch (SocketException error) {
                            if (closed.get()) return -1;
                            throw error;
                        }
                        RtpPcmuCodec.Frame frame =
                            RtpPcmuCodec.parse(receivePacket, datagram.getLength(), expectedRemoteSsrc);
                        if (frame == null) continue;
                        if (lastSequence != -1) {
                            int forward = (frame.sequence() - lastSequence) & 0xffff;
                            if (forward == 0 || forward >= 0x8000) continue; // Replay/late packet.
                        }
                        lastSequence = frame.sequence();
                        pendingRx = frame.pcm16();
                        pendingRxOffset = 0;
                    }
                    return -1;
                }
            }

            @Override public void close() { SessionLease.this.close(); }
        };

        private final OutputStream outgoing = new OutputStream() {
            @Override public void write(int value) throws IOException {
                byte[] one = {(byte) value};
                write(one, 0, 1);
            }

            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                synchronized (txLock) {
                    int cursor = offset;
                    int end = offset + length;
                    while (cursor < end) {
                        if (closed.get()) throw new IOException("voip_media_stopped");
                        int count = Math.min(end - cursor, pendingTx.length - pendingTxCount);
                        System.arraycopy(bytes, cursor, pendingTx, pendingTxCount, count);
                        pendingTxCount += count;
                        cursor += count;
                        if (pendingTxCount == pendingTx.length) {
                            sendCompleteFrame();
                        }
                    }
                }
            }

            private void sendCompleteFrame() throws IOException {
                long target = nextSendAtNs;
                while (target > 0 && !closed.get()) {
                    long remaining = target - System.nanoTime();
                    if (remaining <= 0) break;
                    LockSupport.parkNanos(Math.min(remaining, 5_000_000L));
                }
                if (closed.get()) throw new IOException("voip_media_stopped");
                byte[] packet = RtpPcmuCodec.packetize(
                    pendingTx, 0, outboundSequence, outboundTimestamp, outboundSsrc
                );
                socket.send(new DatagramPacket(packet, packet.length));
                outboundSequence = (outboundSequence + 1) & 0xffff;
                outboundTimestamp = (outboundTimestamp + RtpPcmuCodec.FRAME_SAMPLES) & 0xffff_ffffL;
                pendingTxCount = 0;
                Arrays.fill(pendingTx, (byte) 0);
                nextSendAtNs = System.nanoTime() + RTP_FRAME_NS;
            }

            @Override public void flush() { /* Never send an incomplete 20ms PCM frame. */ }
            @Override public void close() { SessionLease.this.close(); }
        };

        @Override public InputStream downlink() { return incoming; }
        @Override public OutputStream uplink() { return outgoing; }

        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                socket.close(); // Unblocks a concurrent RX without waiting for model/STT.
                // Pending PCM is discarded; STOP never transmits a partial audio frame.
                synchronized (txLock) {
                    pendingTxCount = 0;
                    Arrays.fill(pendingTx, (byte) 0);
                }
            }
        }
    }
}
