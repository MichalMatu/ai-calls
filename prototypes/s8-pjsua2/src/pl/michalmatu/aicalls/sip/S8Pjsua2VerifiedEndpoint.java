package pl.michalmatu.aicalls.sip;

import java.util.Objects;
import org.pjsip.pjsua2.Endpoint;
import org.pjsip.pjsua2.OnTransportStateParam;

/**
 * Prototype PJSUA2 endpoint. Caller owns libCreate/libInit/libStart/libDestroy
 * on a dedicated native worker, and must never use a generic Endpoint instead:
 * that would bypass the TLS certificate/handle registry and deny all INVITEs.
 */
public final class S8Pjsua2VerifiedEndpoint extends Endpoint {
    private final S8Pjsua2TlsAudit tlsAudit;

    public S8Pjsua2VerifiedEndpoint(S8Pjsua2TlsAudit audit) {
        this.tlsAudit = Objects.requireNonNull(audit, "tls audit");
    }

    @Override public void onTransportState(OnTransportStateParam param) {
        tlsAudit.onTransportState(param);
    }

    /** Revoke every known trusted handle before shutting the native endpoint. */
    public void revokeTransportEvidence() {
        tlsAudit.close();
    }
}
