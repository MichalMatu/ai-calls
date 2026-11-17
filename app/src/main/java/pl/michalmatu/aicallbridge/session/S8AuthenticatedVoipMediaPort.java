package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fail-closed PCM boundary for an externally established SIP/SDP/SRTP session.
 * Native SIP/SRTP must still verify TLS, keys, peer and media authentication.
 * No insecure RTP transport is constructed here.
 */
public final class S8AuthenticatedVoipMediaPort
    implements AppOwnedVoipCallMediaSessionBackend.MediaPort {

    private final Object identity;
    private final S8SecureSipDialogGate gate;
    private final S8SecureSipDialogGate.VerifiedMedia nativeMedia;
    private final AppOwnedVoipCallMediaSessionBackend.MediaPort delegate;

    public S8AuthenticatedVoipMediaPort(
        Object identity,
        S8SecureSipDialogGate gate,
        S8SecureSipDialogGate.VerifiedMedia nativeMedia,
        AppOwnedVoipCallMediaSessionBackend.MediaPort delegate
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.nativeMedia = Objects.requireNonNull(nativeMedia, "nativeMedia");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    private boolean authenticated(Object requestedIdentity) {
        return requestedIdentity == identity
            && gate.snapshot().mediaReady()
            && nativeMedia.exactCallIdentity() == identity
            && nativeMedia.mediaSecurity()
                == S8SecureSipDialogGate.MediaSecurity.TLS_AND_SRTP_AUTHENTICATED
            && delegate.activeIdentity() == identity;
    }

    @Override public Object activeIdentity() {
        return authenticated(identity) ? identity : null;
    }

    @Override public boolean mediaHealthy(Object exactIdentity) {
        return authenticated(exactIdentity) && delegate.mediaHealthy(identity);
    }

    @Override public CallMediaEndpointLease open(
        Object exactIdentity, long generation, int sampleRateHz
    ) {
        if (generation <= 0 || sampleRateHz != 16_000
            || !authenticated(exactIdentity)) {
            throw new SecurityException("s8_authenticated_srtp_required");
        }
        // Native opening is external and may block. Recheck after it returns.
        CallMediaEndpointLease candidate = Objects.requireNonNull(
            delegate.open(identity, generation, sampleRateHz),
            "s8_native_media_lease_missing"
        );
        if (!authenticated(identity)) {
            candidate.close();
            throw new SecurityException("s8_srtp_lost_during_open");
        }
        return new GuardedLease(candidate);
    }

    private final class GuardedLease implements CallMediaEndpointLease {
        private final CallMediaEndpointLease raw;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final InputStream downlink;
        private final OutputStream uplink;

        GuardedLease(CallMediaEndpointLease raw) {
            this.raw = raw;
            InputStream input = Objects.requireNonNull(raw.downlink(), "downlink");
            OutputStream output = Objects.requireNonNull(raw.uplink(), "uplink");
            downlink = new InputStream() {
                @Override public int read() throws IOException {
                    byte[] one = new byte[1];
                    return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
                }
                @Override public int read(byte[] bytes, int off, int len) throws IOException {
                    Objects.checkFromIndexSize(off, len, bytes.length);
                    if (len == 0) return 0;
                    requireSecure();
                    int received = input.read(bytes, off, len);
                    if (!valid()) {
                        if (received > 0) Arrays.fill(bytes, off, off + received, (byte) 0);
                        throw new IOException("s8_srtp_revoked_during_rx");
                    }
                    return received;
                }
            };
            uplink = new OutputStream() {
                @Override public void write(int value) throws IOException {
                    requireSecure();
                    output.write(value);
                }
                @Override public void write(byte[] bytes, int off, int len)
                    throws IOException {
                    Objects.checkFromIndexSize(off, len, bytes.length);
                    if (len == 0) return;
                    requireSecure();
                    output.write(bytes, off, len);
                }
            };
        }

        private boolean valid() {
            return !closed.get() && authenticated(identity);
        }

        private void requireSecure() throws IOException {
            if (!valid()) throw new IOException("s8_authenticated_srtp_required");
        }

        @Override public InputStream downlink() { return downlink; }
        @Override public OutputStream uplink() { return uplink; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) raw.close();
        }
    }
}
