package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.util.Objects;

/**
 * Single, explicitly approved SIPS/TLS REGISTER attempt: REGISTER -> 401
 * SHA-256 Digest -> REGISTER -> 200. Never registers automatically.
 *
 * Experimental strict subset: trusted platform CA + DNS hostname, one exact
 * Contact binding, one 401, no renewal/keepalive, redirects, multiple contacts,
 * SIP URI-SAN-only certs, INVITE, RTP or SRTP. Do not treat success as media
 * readiness or as Android GSM access. Caller owns password char[] and must
 * clear it after use. No logs of SIP headers, password or audio.
 *
 * Android: execute off the UI thread. An explicit approval for THIS specific
 * registrar and identity is required; do not reuse approval for another host.
 */
public final class S8SipsTlsRegisterClient {
    /** No SIP identity or secrets are exposed to callers in a receipt. */
    public record RegistrationReceipt(int acceptedExpiresSeconds) {}

    private S8SipsTlsRegisterClient() {}

    /**
     * Production transport is available but has NO automatic caller in S8.
     * This entry point is network-capable only when explicitlyApproved=true.
     */
    public static RegistrationReceipt registerOnce(
        S8SipsRegisterDraft.Config config, char[] password,
        int timeoutMs, boolean explicitlyApproved
    ) throws IOException {
        return registerOnce(config, password, timeoutMs, explicitlyApproved,
            S8SipsTlsOptionsProbe::openPlatformChannel);
    }

    /** Test seam: never opens a real socket with an injected channel. */
    static RegistrationReceipt registerOnce(
        S8SipsRegisterDraft.Config config, char[] password,
        int timeoutMs, boolean explicitlyApproved,
        S8SipsTlsOptionsProbe.Connector connector
    ) throws IOException {
        // Approval precedes any configuration parsing, DNS, socket or secret use.
        if (!explicitlyApproved) {
            throw new SecurityException("sips_register_requires_explicit_approval");
        }
        if (timeoutMs < S8SipsTlsOptionsProbe.MIN_TIMEOUT_MS
            || timeoutMs > S8SipsTlsOptionsProbe.MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("sips_register_timeout_out_of_bounds");
        }
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(password, "password");
        if (password.length == 0 || password.length > 1024) {
            throw new IllegalArgumentException("sips_register_password_length");
        }

        // Validates registrar and Contact, creates one uncredentialed REGISTER.
        // Nothing is sent until a verified TLS channel becomes available.
        S8SipsRegisterDraft.Draft initial = S8SipsRegisterDraft.initial(config);
        S8SipsOptionsProtocol.Endpoint registrar = S8SipsOptionsProtocol.parseEndpoint(
            "sips:" + config.registrar().host() + ":" + config.registrar().port()
        );
        var exchange = S8SipsRegisterDryRun.exchangeOnce(
            initial, password, connector.open(registrar, timeoutMs)
        );
        return new RegistrationReceipt(exchange.acceptedExpiresSeconds());
    }
}
