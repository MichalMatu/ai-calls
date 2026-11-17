package pl.michalmatu.aicallbridge.session;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * S8 fail-closed boundary between a future native SIP/SDP/SRTP engine and the app.
 *
 * This is a lifecycle coordinator, NOT a SIP parser, cryptographic library or
 * network client. An authenticated PJSUA2 (or equivalent) implementation must
 * supply VerifiedMedia and Signaling; without it no call can become ACTIVE.
 * Bare RTP/AVP from the localhost lab is never accepted for external calls.
 */
public final class S8SecureSipDialogGate implements AutoCloseable {
    public enum State { IDLE, RINGING, NEGOTIATING, ACTIVE, STOPPED, FAILED }
    public enum MediaSecurity { UNVERIFIED, TLS_AND_SRTP_AUTHENTICATED }

    /**
     * Adapter is implemented only by an audited SIP+SRTP stack, not by a caller
     * asking for a mode switch. It must validate remote identity, SDP answer and
     * SRTP keying before mediaSecurity() returns authenticated.
     */
    public interface VerifiedMedia extends AutoCloseable {
        MediaSecurity mediaSecurity();
        Object exactCallIdentity();
        @Override void close();
    }

    /** Native signaling owns INVITE, SDP, ACK, BYE and CANCEL on verified TLS. */
    public interface Signaling extends AutoCloseable {
        boolean tlsPeerVerified();
        void answerInvite(Object callIdentity);
        void rejectInvite(Object callIdentity);
        void endDialog(Object callIdentity);
        void acknowledgeRemoteBye(Object callIdentity);
        @Override void close();
    }

    public record Snapshot(State state, long generation, boolean mediaReady) {}

    private final Object lock = new Object();
    private final Signaling signaling;
    private final VerifiedMedia media;
    private final Consumer<Snapshot> observer;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private Object identity;
    private State state = State.IDLE;
    private long generation;

    public S8SecureSipDialogGate(
        Signaling signaling, VerifiedMedia media, Consumer<Snapshot> observer
    ) {
        this.signaling = Objects.requireNonNull(signaling, "signaling");
        this.media = Objects.requireNonNull(media, "media");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    /** Native SIP stack only: callback after receipt of a verified TLS INVITE. */
    public boolean onVerifiedIncomingInvite(Object exactIdentity) {
        Objects.requireNonNull(exactIdentity, "identity");
        final boolean tlsVerified = signaling.tlsPeerVerified();
        final Object mediaIdentity = media.exactCallIdentity();
        final Snapshot result;
        synchronized (lock) {
            if (stopped.get() || state != State.IDLE || !tlsVerified
                || mediaIdentity != exactIdentity) {
                return false;
            }
            identity = exactIdentity;
            generation++;
            state = State.RINGING;
            result = snapshotLocked();
        }
        publish(result);
        return true;
    }

    /** Explicit user acceptance; never reports ACTIVE before authenticated SRTP. */
    public boolean accept(Object exactIdentity) {
        final boolean tlsVerified = signaling.tlsPeerVerified();
        final Snapshot result;
        synchronized (lock) {
            if (stopped.get() || state != State.RINGING || identity != exactIdentity
                || !tlsVerified) {
                return false;
            }
            state = State.NEGOTIATING;
            result = snapshotLocked();
        }
        publish(result);
        if (stopped.get()) return false;
        try {
            // The native adapter must cancel pending writes once close() occurs.
            signaling.answerInvite(exactIdentity);
            return true;
        } catch (RuntimeException error) {
            fail();
            return false;
        }
    }

    /** A completed SDP exchange is insufficient: verify live SRTP keys first. */
    public boolean onAuthenticatedSrtp(Object exactIdentity) {
        final Object ownedMedia = media.exactCallIdentity();
        final MediaSecurity secured = media.mediaSecurity();
        final boolean tlsVerified = signaling.tlsPeerVerified();
        final Snapshot result;
        synchronized (lock) {
            if (stopped.get() || state != State.NEGOTIATING || identity != exactIdentity
                || ownedMedia != exactIdentity
                || secured != MediaSecurity.TLS_AND_SRTP_AUTHENTICATED
                || !tlsVerified) {
                return false;
            }
            state = State.ACTIVE;
            result = snapshotLocked();
        }
        publish(result);
        return true;
    }

    /** Authenticated media is lost: immediately revoke PCM and terminate dialog. */
    public void onMediaSecurityLost(Object exactIdentity) {
        synchronized (lock) {
            if (identity != exactIdentity || stopped.get()) return;
        }
        fail();
    }

    /** Media must be revoked even when signaling is still blocked. */
    public void takeOverNow() {
        finish(State.STOPPED);
    }

    public void onRemoteBye(Object exactIdentity) {
        synchronized (lock) {
            if (identity != exactIdentity || stopped.get()) return;
        }
        finish(State.STOPPED, true);
    }

    private void fail() { finish(State.FAILED); }

    private void finish(State terminal) { finish(terminal, false); }

    private void finish(State terminal, boolean remoteBye) {
        if (!stopped.compareAndSet(false, true)) return;
        final Snapshot result;
        final Object owned;
        final State previous;
        synchronized (lock) {
            owned = identity;
            previous = state;
            state = terminal;
            result = snapshotLocked();
        }
        // Revoke encrypted PCM before any potentially blocking native SIP call.
        try { media.close(); } catch (RuntimeException ignored) {}
        publish(result);
        if (owned != null) {
            try {
                if (previous == State.RINGING) signaling.rejectInvite(owned);
                else if (previous == State.NEGOTIATING || previous == State.ACTIVE) {
                    if (remoteBye) signaling.acknowledgeRemoteBye(owned);
                    else signaling.endDialog(owned);
                }
            } catch (RuntimeException ignored) {}
        }
        try { signaling.close(); } catch (RuntimeException ignored) {}
    }

    @Override public void close() { takeOverNow(); }

    private Snapshot snapshotLocked() {
        // ACTIVE is entered only after native-authenticated SRTP evidence.
        return new Snapshot(state, generation, state == State.ACTIVE);
    }

    private void publish(Snapshot result) {
        try { observer.accept(result); } catch (RuntimeException ignored) {}
    }
}
