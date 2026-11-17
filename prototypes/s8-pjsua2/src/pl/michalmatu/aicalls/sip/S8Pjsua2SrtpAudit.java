package pl.michalmatu.aicalls.sip;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Live native SRTP attestation boundary, separate from SDP configuration.
 *
 * Both predicates must hold on every audio callback:
 *  1) caller-supplied native TLS transport verification (not a UI flag)
 *  2) pjmedia_srtp_info.active with RX and TX crypto negotiated.
 *
 * Missing JNI, pending keying and dropped TLS always return false.
 * Isolated prototype; never auto-loads or ships a native library.
 */
public final class S8Pjsua2SrtpAudit {
    private static final AtomicBoolean available = new AtomicBoolean(false);

    private S8Pjsua2SrtpAudit() {}

    /** Explicit prototype initialization. Native product packaging is held. */
    public static boolean loadPrototypeVerifier() {
        if (available.get()) return true;
        try {
            System.loadLibrary("s8srtpaudit");
            available.set(true);
            return true;
        } catch (LinkageError ignored) {
            return false;
        }
    }

    public static boolean isActive(
        int nativeCallId, int audioMediaIndex, BooleanSupplier tlsVerifiedNow
    ) {
        if (!available.get() || nativeCallId < 0 ||
            audioMediaIndex < 0 || audioMediaIndex > 15 ||
            tlsVerifiedNow == null) return false;
        final boolean verified;
        try { verified = tlsVerifiedNow.getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
        if (!verified) return false;
        try {
            final boolean active = nativeSrtpActive(nativeCallId, audioMediaIndex);
            // Re-check after native operation; TLS may have been revoked.
            return active && tlsVerifiedNow.getAsBoolean();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static native boolean nativeSrtpActive(int callId, int mediaIndex);
}
