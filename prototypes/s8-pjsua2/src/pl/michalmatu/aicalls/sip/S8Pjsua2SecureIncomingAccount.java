package pl.michalmatu.aicalls.sip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.Call;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.OnCallStateParam;
import org.pjsip.pjsua2.OnCallMediaStateParam;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.TlsConfig;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.pjmedia_srtp_use;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;

/**
 * Isolated PJSUA2 2.17 Android ARM64 prototype. Not built into product APK.
 *
 * Handles native INVITE, 180 Ringing, explicit user answer/reject and BYE.
 * This does NOT attest actual SRTP establishment or expose PCM to an AI:
 * the app's S8SecureSipDialogGate remains closed until separate audited
 * native keying/transport verification. No auto-answer or outgoing calls.
 */
public final class S8Pjsua2SecureIncomingAccount extends Account
    implements AutoCloseable {

    public interface Listener {
        // Opaque token only: no SIP URI or phone number is emitted here.
        void onIncoming(Object exactIdentity);
        void onMediaStateChanged(Object exactIdentity);
        void onEnded(Object exactIdentity);
        void onNativeError(String safeCode);
    }

    private final Listener listener;
    private final Object lock = new Object();
    private final List<Incoming> rejected = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Incoming current;

    public S8Pjsua2SecureIncomingAccount(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Force TLS-only SIPS account and SRTP_MANDATORY, never OPTIONAL.
     * Registration deliberately remains disabled until explicit authorization.
     * Callers must pass an actual PJSUA2 TLS transport ID from Endpoint.
     */
    public static AccountConfig secureAccountConfig(
        String localSipsUri, String registrarSipsUri, int tlsTransportId
    ) {
        if (tlsTransportId <= 0 ||
            localSipsUri == null || !localSipsUri.startsWith("sips:") ||
            registrarSipsUri == null || !registrarSipsUri.startsWith("sips:")) {
            throw new IllegalArgumentException("s8_tls_sips_account_required");
        }
        AccountConfig config = new AccountConfig();
        config.setIdUri(localSipsUri);
        config.getRegConfig().setRegistrarUri(registrarSipsUri);
        config.getRegConfig().setRegisterOnAdd(false);
        config.getSipConfig().setTransportId(tlsTransportId);
        config.getMediaConfig().setSrtpUse(
            pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY
        );
        config.getMediaConfig().setSrtpSecureSignaling(2);
        return config;
    }

    /**
     * TLS must verify the server against a trusted CA bundle. Never disable
     * certificate verification to make a prototype connect.
     */
    public static TransportConfig verifiedTlsTransport(String trustedCaBundlePath) {
        if (trustedCaBundlePath == null || trustedCaBundlePath.isBlank() ||
            !new java.io.File(trustedCaBundlePath).isFile()) {
            throw new IllegalArgumentException("s8_trusted_ca_bundle_required");
        }
        TlsConfig tls = new TlsConfig();
        tls.setVerifyServer(true);
        tls.setCaListFile(trustedCaBundlePath);
        TransportConfig transport = new TransportConfig();
        transport.setPort(0);
        transport.setTlsConfig(tls);
        return transport;
    }

    /** Exact currently owned call handle, never a telephone number or URI. */
    public int activeNativeCallId(Object exactIdentity) {
        synchronized (lock) {
            if (closed.get() || current == null ||
                current.identity != exactIdentity) return -1;
            return current.getId();
        }
    }

    /** Audio access only for exact active token; caller MUST separately attest SRTP. */
    public AudioMedia activeAudioMedia(Object exactIdentity, int mediaIndex)
        throws java.lang.Exception {
        final Incoming call;
        synchronized (lock) {
            if (closed.get() || current == null || mediaIndex < 0 ||
                mediaIndex > 15 || current.identity != exactIdentity) {
                throw new SecurityException("s8_native_call_identity_required");
            }
            call = current;
        }
        return call.getAudioMedia(mediaIndex);
    }

    /** Initiate only through a separate explicit account setup/registration flow. */
    public void createSecured(AccountConfig config) throws java.lang.Exception {
        if (config.getMediaConfig().getSrtpUse() !=
                pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY ||
            config.getMediaConfig().getSrtpSecureSignaling() != 2 ||
            config.getRegConfig().getRegisterOnAdd() ||
            config.getSipConfig().getTransportId() <= 0 ||
            !config.getIdUri().startsWith("sips:") ||
            !config.getRegConfig().getRegistrarUri().startsWith("sips:")) {
            throw new SecurityException("s8_sip_account_policy_downgraded");
        }
        if (closed.get()) throw new IllegalStateException("s8_sip_closed");
        create(config, false);
    }

    @Override public void onIncomingCall(OnIncomingCallParam event) {
        final Incoming incoming = new Incoming(event.getCallId());
        final boolean accepted;
        synchronized (lock) {
            accepted = !closed.get() && current == null;
            if (accepted) current = incoming;
            else rejected.add(incoming);
        }
        if (!accepted) {
            try {
                CallOpParam busy = new CallOpParam();
                busy.setStatusCode(pjsip_status_code.PJSIP_SC_BUSY_HERE);
                incoming.answer(busy);
            } catch (java.lang.Exception error) {
                listener.onNativeError("s8_native_busy_reject_failed");
            }
            return;
        }
        try {
            CallOpParam ringing = new CallOpParam();
            ringing.setStatusCode(pjsip_status_code.PJSIP_SC_RINGING);
            incoming.answer(ringing);
            listener.onIncoming(incoming.identity);
        } catch (java.lang.Exception error) {
            listener.onNativeError("s8_native_ringing_failed");
            reject(incoming.identity);
        }
    }

    /** Only an explicit button tap can send 200 OK. This is not SRTP proof. */
    public boolean accept(Object exactIdentity) {
        final Incoming incoming;
        synchronized (lock) {
            incoming = current;
            if (closed.get() || incoming == null ||
                incoming.identity != exactIdentity || incoming.answered) return false;
            incoming.answered = true;
        }
        try {
            CallOpParam answer = new CallOpParam();
            answer.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
            incoming.answer(answer);
            return true;
        } catch (java.lang.Exception error) {
            listener.onNativeError("s8_native_answer_failed");
            hangUp(exactIdentity);
            return false;
        }
    }

    public boolean reject(Object exactIdentity) {
        final Incoming incoming;
        synchronized (lock) {
            incoming = current;
            if (incoming == null || incoming.identity != exactIdentity ||
                incoming.answered) return false;
            current = null;
            rejected.add(incoming); // Keep SWIG object alive until DISCONNECTED.
        }
        try {
            CallOpParam declined = new CallOpParam();
            declined.setStatusCode(pjsip_status_code.PJSIP_SC_DECLINE);
            incoming.answer(declined);
            return true;
        } catch (java.lang.Exception error) {
            listener.onNativeError("s8_native_decline_failed");
            return false;
        }
    }

    public void hangUp(Object exactIdentity) {
        final Incoming incoming;
        synchronized (lock) {
            incoming = current;
            if (incoming == null || incoming.identity != exactIdentity) return;
            current = null;
            rejected.add(incoming);
        }
        try {
            incoming.hangup(new CallOpParam());
        } catch (java.lang.Exception error) {
            listener.onNativeError("s8_native_bye_failed");
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        final Object owned;
        synchronized (lock) { owned = current == null ? null : current.identity; }
        if (owned != null) hangUp(owned);
        // Native Account shutdown/Endpoint lifetime is owned by the host.
        // Host stops native callbacks and then calls Account.delete().
    }

    private final class Incoming extends Call {
        final Object identity = new Object();
        boolean answered;

        Incoming(int nativeCallId) { super(S8Pjsua2SecureIncomingAccount.this, nativeCallId); }

        @Override public void onCallMediaState(OnCallMediaStateParam ignored) {
            final boolean owned;
            synchronized (lock) {
                owned = !closed.get() && current == this;
            }
            if (owned) listener.onMediaStateChanged(identity);
        }

        @Override public void onCallState(OnCallStateParam ignored) {
            try {
                CallInfo info = getInfo();
                if (info.getState() != pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) return;
            } catch (java.lang.Exception error) {
                listener.onNativeError("s8_native_state_unavailable");
                return;
            }
            synchronized (lock) {
                if (current == this) current = null;
                rejected.remove(this);
            }
            listener.onEnded(identity);
            // PJSUA2 Call lifetime and deletion are managed on native worker
            // after disconnect; never delete SWIG Call inside its own callback.
        }
    }
}
