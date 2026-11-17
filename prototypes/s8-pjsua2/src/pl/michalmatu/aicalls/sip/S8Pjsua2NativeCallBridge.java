package pl.michalmatu.aicalls.sip;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.pjsip.pjsua2.AudioMedia;
import pl.michalmatu.aicallbridge.session.AppOwnedVoipCallMediaSessionBackend;
import pl.michalmatu.aicallbridge.session.CallMediaEndpointLease;
import pl.michalmatu.aicallbridge.session.S8SecureSipDialogGate;

/**
 * Isolated PJSUA2 2.17 -> S8 app media contract.
 *
 * Assemble exactly once, inside the trusted incoming call callback. The host
 * passes verifiedMedia(), signaling() and mediaPort() to its
 * S8SecureVoipSpeechController, calls attachAuthenticatedMedia() only from a
 * native media-state callback, then onAuthenticatedSrtp(exactIdentity).
 *
 * No native bindings or GPL library are packaged in the product APK.
 */
public final class S8Pjsua2NativeCallBridge {
    private static final int PCM_FRAME_BYTES = 640;
    private static final long RX_WAIT_MS = 5L;

    private final Object identity;
    private final S8Pjsua2SecureIncomingAccount account;
    private final S8Pjsua2TlsAudit tls;
    private final int mediaIndex;
    private final long trustedHandle;
    private final Object lock = new Object();
    private final AtomicBoolean revoked = new AtomicBoolean();
    private final AtomicBoolean signalingStopped = new AtomicBoolean();

    private S8Pjsua2PcmPort pcm;
    private AudioMedia nativeAudio;
    private boolean leased;

    public S8Pjsua2NativeCallBridge(
        Object identity, S8Pjsua2SecureIncomingAccount account,
        S8Pjsua2TlsAudit tls, int audioMediaIndex
    ) {
        this.identity = Objects.requireNonNull(identity, "exact SIP identity");
        this.account = Objects.requireNonNull(account, "native SIP account");
        this.tls = Objects.requireNonNull(tls, "native TLS registry");
        if (audioMediaIndex < 0 || audioMediaIndex > 15)
            throw new IllegalArgumentException("s8_invalid_media_index");
        this.mediaIndex = audioMediaIndex;
        this.trustedHandle = account.trustedTransportHandle(identity);
        if (trustedHandle == 0L || !tls.stillVerified(trustedHandle))
            throw new SecurityException("s8_native_verified_invite_required");
    }

    private boolean liveTls() {
        return !revoked.get() && !signalingStopped.get() &&
            tls.stillVerified(trustedHandle) &&
            account.trustedTransportHandle(identity) == trustedHandle &&
            account.activeNativeCallId(identity) >= 0;
    }

    /** No mediaReady based on SDP or SRTP_MANDATORY configuration alone. */
    private boolean liveSrtp() {
        if (!liveTls()) return false;
        final int nativeId = account.activeNativeCallId(identity);
        return nativeId >= 0 &&
            S8Pjsua2SrtpAudit.isActive(
                nativeId, mediaIndex, this::liveTls
            );
    }

    public S8SecureSipDialogGate.Signaling signaling() {
        return new S8SecureSipDialogGate.Signaling() {
            @Override public boolean tlsPeerVerified() { return liveTls(); }
            @Override public void answerInvite(Object token) {
                requireExact(token);
                if (!account.accept(identity))
                    throw new IllegalStateException("s8_native_answer_refused");
            }
            @Override public void rejectInvite(Object token) {
                requireExact(token);
                account.reject(identity);
            }
            @Override public void endDialog(Object token) {
                requireExact(token);
                account.hangUp(identity);
            }
            @Override public void acknowledgeRemoteBye(Object token) {
                requireExact(token);
                // PJSUA2 sends SIP 200 for authenticated BYE automatically.
            }
            @Override public void acknowledgeRemoteCancel(Object token) {
                requireExact(token);
                // PJSUA2 completes the INVITE transaction automatically.
            }
            @Override public void close() { signalingStopped.set(true); }
        };
    }

    public S8SecureSipDialogGate.VerifiedMedia verifiedMedia() {
        return new S8SecureSipDialogGate.VerifiedMedia() {
            @Override public Object exactCallIdentity() { return identity; }
            @Override public S8SecureSipDialogGate.MediaSecurity mediaSecurity() {
                return liveSrtp() && attached()
                    ? S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED
                    : S8SecureSipDialogGate.MediaSecurity.UNVERIFIED;
            }
            @Override public void close() { revokeMedia(); }
        };
    }

    public AppOwnedVoipCallMediaSessionBackend.MediaPort mediaPort() {
        return new AppOwnedVoipCallMediaSessionBackend.MediaPort() {
            @Override public Object activeIdentity() {
                return attached() && liveSrtp() ? identity : null;
            }
            @Override public boolean mediaHealthy(Object token) {
                return token == identity && attached() && liveSrtp();
            }
            @Override public CallMediaEndpointLease open(
                Object token, long generation, int sampleRateHz
            ) {
                if (token != identity || generation <= 0 || sampleRateHz != 16_000
                    || !attached() || !liveSrtp()) {
                    throw new SecurityException("s8_authenticated_native_pcm_required");
                }
                synchronized (lock) {
                    if (revoked.get() || leased || pcm == null || !liveSrtp())
                        throw new SecurityException("s8_native_lease_unavailable");
                    leased = true;
                    return new PcmLease(pcm);
                }
            }
        };
    }

    /**
     * Native media-state callback only, after answer and actual SRTP keys.
     * Must run off the Android main/UI thread. No PCM opens on SDP alone.
     */
    public boolean attachAuthenticatedMedia() {
        if (!liveSrtp()) return false;
        S8Pjsua2PcmPort candidate = null;
        AudioMedia peer = null;
        try {
            candidate = S8Pjsua2PcmPort.forVerifiedNativeCall(
                account.activeNativeCallId(identity), mediaIndex, tls, trustedHandle
            );
            candidate.createMonoPcm16Port();
            peer = account.activeAudioMedia(identity, mediaIndex);
            candidate.connectAuthenticated(peer);
        } catch (Exception | LinkageError failed) {
            if (candidate != null) {
                if (peer != null) candidate.disconnect(peer);
                else candidate.close();
            }
            return false;
        }
        synchronized (lock) {
            if (!revoked.get() && pcm == null && liveSrtp()) {
                pcm = candidate;
                nativeAudio = peer;
                return true;
            }
        }
        candidate.disconnect(peer);
        return false;
    }

    private boolean attached() {
        synchronized (lock) {
            return !revoked.get() && pcm != null && nativeAudio != null;
        }
    }

    private void requireExact(Object token) {
        if (token != identity || signalingStopped.get())
            throw new SecurityException("s8_native_sip_identity_required");
    }

    public void revokeMedia() {
        if (!revoked.compareAndSet(false, true)) return;
        final S8Pjsua2PcmPort old;
        final AudioMedia peer;
        synchronized (lock) {
            old = pcm;
            peer = nativeAudio;
            pcm = null;
            nativeAudio = null;
        }
        if (old != null) {
            if (peer != null) old.disconnect(peer);
            else old.close();
        }
    }

    /**
     * 20-ms frames are queued, not retained in callbacks. A native security
     * downgrade immediately revokes input and output with IOException.
     */
    private final class PcmLease implements CallMediaEndpointLease {
        private final S8Pjsua2PcmPort bound;
        private final AtomicBoolean released = new AtomicBoolean();
        private final byte[] pendingRx = new byte[PCM_FRAME_BYTES];
        private int pendingOffset = PCM_FRAME_BYTES;
        private final byte[] pendingTx = new byte[PCM_FRAME_BYTES];
        private int txOffset;
        private final InputStream input = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }
            @Override public int read(byte[] dst, int off, int length)
                throws IOException {
                Objects.checkFromIndexSize(off, length, dst.length);
                if (length == 0) return 0;
                while (true) {
                    requireMedia();
                    if (pendingOffset < PCM_FRAME_BYTES) {
                        int copied = Math.min(length, PCM_FRAME_BYTES - pendingOffset);
                        System.arraycopy(pendingRx, pendingOffset, dst, off, copied);
                        Arrays.fill(pendingRx, pendingOffset,
                            pendingOffset + copied, (byte) 0);
                        pendingOffset += copied;
                        if (!liveSrtp() || released.get()) {
                            Arrays.fill(dst, off, off + copied, (byte) 0);
                            throw new IOException("s8_native_security_lost_rx");
                        }
                        return copied;
                    }
                    if (bound.pollInput(pendingRx) == PCM_FRAME_BYTES) {
                        pendingOffset = 0;
                        continue;
                    }
                    try {
                        Thread.sleep(RX_WAIT_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("s8_native_pcm_rx_stopped");
                    }
                }
            }
        };
        private final OutputStream output = new OutputStream() {
            @Override public void write(int byteValue) throws IOException {
                write(new byte[]{(byte) byteValue}, 0, 1);
            }
            @Override public void write(byte[] data, int off, int len)
                throws IOException {
                Objects.checkFromIndexSize(off, len, data.length);
                synchronized (PcmLease.this) {
                    requireMedia();
                    while (len > 0) {
                        int count = Math.min(len, PCM_FRAME_BYTES - txOffset);
                        System.arraycopy(data, off, pendingTx, txOffset, count);
                        txOffset += count;
                        off += count;
                        len -= count;
                        if (txOffset == PCM_FRAME_BYTES) {
                            requireMedia();
                            if (!bound.offerApprovedOutput(pendingTx))
                                throw new IOException("s8_native_pcm_tx_unavailable");
                            Arrays.fill(pendingTx, (byte) 0);
                            txOffset = 0;
                        }
                    }
                }
            }
        };

        private PcmLease(S8Pjsua2PcmPort bound) { this.bound = bound; }

        private void requireMedia() throws IOException {
            if (released.get() || revoked.get() || !liveSrtp())
                throw new IOException("s8_authenticated_native_pcm_required");
        }

        @Override public InputStream downlink() { return input; }
        @Override public OutputStream uplink() { return output; }

        @Override public void close() {
            if (!released.compareAndSet(false, true)) return;
            Arrays.fill(pendingRx, (byte) 0);
            synchronized (this) {
                Arrays.fill(pendingTx, (byte) 0);
                txOffset = 0;
            }
            revokeMedia();
        }
    }
}
