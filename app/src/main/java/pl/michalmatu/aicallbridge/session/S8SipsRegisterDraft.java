package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Pure SIP REGISTER request construction, NOT a registrar client.
 *
 * Requires caller-supplied, already verified reachable SIPS Contact; no
 * network/DNS, credentials store, TLS socket, automatic renewal or INVITE.
 * This is deliberately disconnected from S8SipsTlsOptionsProbe. Sending any
 * REGISTER requires a separate approved operation and a validated SIP flow.
 */
public final class S8SipsRegisterDraft {
    public record Config(
        S8SipsOptionsProtocol.Endpoint registrar,
        String user,
        S8SipsOptionsProtocol.Endpoint contact,
        int expiresSeconds
    ) {}

    /** Includes auth material after retry. Never log or persist a Draft.wire(). */
    public static final class Draft {
        private final Config config;
        private final String callId;
        private final String fromTag;
        private final String viaBranch;
        private final int cseq;
        private final String wire;

        private Draft(Config config, String callId, String fromTag, String viaBranch,
                      int cseq, String wire) {
            this.config = config;
            this.callId = callId;
            this.fromTag = fromTag;
            this.viaBranch = viaBranch;
            this.cseq = cseq;
            this.wire = wire;
        }

        public String callId() { return callId; }
        public String viaBranch() { return viaBranch; }
        public int cseq() { return cseq; }
        public String wire() { return wire; }
        String viaSentBy() {
            return config.contact().host() + ":" + config.contact().port();
        }
        String expectedAor() {
            return "<sips:" + config.user() + "@" + config.registrar().host() + ">";
        }
        String expectedFrom() { return expectedAor() + ";tag=" + fromTag; }
        String expectedContact() {
            return "<sips:" + config.user() + "@" + viaSentBy() + ";transport=tls>";
        }
        int requestedExpires() { return config.expiresSeconds(); }
        boolean matchesToHeader(String value) {
            String aor = expectedAor();
            if (value == null) return false;
            if (value.equals(aor)) return true;
            String prefix = aor + ";tag=";
            if (!value.startsWith(prefix) || value.length() <= prefix.length()
                || value.length() > prefix.length() + 64) return false;
            for (int i = prefix.length(); i < value.length(); i++) {
                char c = value.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '.')) return false;
            }
            return true;
        }
    }

    private S8SipsRegisterDraft() {}

    /** 1st attempt carries no auth. Never transmits anything. */
    public static Draft initial(Config input) {
        Config config = check(input);
        String callId = S8SipDigestSha256.newCnonce() + "@" + config.contact().host();
        String fromTag = S8SipDigestSha256.newCnonce();
        String branch = "z9hG4bK" + S8SipDigestSha256.newCnonce();
        return new Draft(config, callId, fromTag, branch, 1,
            serialize(config, callId, fromTag, branch, 1, null));
    }

    /**
     * One and only one 401 retry with RFC 8760 SHA-256 qop=auth, CSeq 2 and
     * a fresh Via branch. Requires the caller to have validated the 401
     * belongs to this transaction on the same authenticated TLS connection.
     */
    public static Draft retryAfterValidated401(
        Draft first, S8SipDigestSha256.Challenge challenge, char[] password
    ) {
        Objects.requireNonNull(first, "first");
        if (first.cseq != 1) {
            throw new IllegalStateException("sip_register_auth_retry_exhausted");
        }
        String auth = S8SipDigestSha256.registerAuthorization(
            challenge, first.config.user(), password, first.config.registrar(),
            S8SipDigestSha256.newCnonce()
        );
        String branch = "z9hG4bK" + S8SipDigestSha256.newCnonce();
        return new Draft(first.config, first.callId, first.fromTag, branch, 2,
            serialize(first.config, first.callId, first.fromTag, branch, 2, auth));
    }

    /** Fail closed on forged/mismatched 401 before using any password. Offline parser only. */
    public static Draft retryAfter401Response(
        Draft first, InputStream response, char[] password
    ) throws IOException {
        Objects.requireNonNull(first, "first");
        if (first.cseq != 1) {
            throw new IllegalStateException("sip_register_auth_retry_exhausted");
        }
        S8SipDigestSha256.Challenge challenge =
            S8SipsRegister401.readChallenge(response, first);
        return retryAfterValidated401(first, challenge, password);
    }

    private static Config check(Config input) {
        Objects.requireNonNull(input, "config");
        Objects.requireNonNull(input.registrar(), "registrar");
        Objects.requireNonNull(input.contact(), "contact");
        S8SipsOptionsProtocol.Endpoint registrar = S8SipsOptionsProtocol.parseEndpoint(
            "sips:" + input.registrar().host() + ":" + input.registrar().port()
        );
        S8SipsOptionsProtocol.Endpoint contact = S8SipsOptionsProtocol.parseEndpoint(
            "sips:" + input.contact().host() + ":" + input.contact().port()
        );
        if (input.expiresSeconds() < 60 || input.expiresSeconds() > 3600) {
            throw new IllegalArgumentException("sip_register_expiry_not_supported");
        }
        String user = input.user();
        if (user == null || user.isEmpty() || user.length() > 128) {
            throw new IllegalArgumentException("sip_register_user_invalid");
        }
        for (int i = 0; i < user.length(); i++) {
            char c = user.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-')) {
                throw new IllegalArgumentException("sip_register_user_invalid");
            }
        }
        return new Config(registrar, user, contact, input.expiresSeconds());
    }

    private static String serialize(
        Config config, String callId, String fromTag,
        String branch, int cseq, String authorization
    ) {
        String requestUri = "sips:" + config.registrar().host()
            + (config.registrar().port() == 5061 ? "" : ":" + config.registrar().port());
        String aor = "sips:" + config.user() + "@" + config.registrar().host();
        String via = config.contact().host() + ":" + config.contact().port();
        String contactUri = "sips:" + config.user() + "@" + via + ";transport=tls";
        StringBuilder result = new StringBuilder(680);
        result.append("REGISTER ").append(requestUri).append(" SIP/2.0\r\n")
            .append("Via: SIP/2.0/TLS ").append(via)
            .append(";branch=").append(branch).append(";rport\r\n")
            .append("Max-Forwards: 70\r\n")
            .append("To: <").append(aor).append(">\r\n")
            .append("From: <").append(aor).append(">;tag=").append(fromTag)
            .append("\r\n")
            .append("Call-ID: ").append(callId).append("\r\n")
            .append("CSeq: ").append(cseq).append(" REGISTER\r\n")
            .append("Contact: <").append(contactUri).append(">\r\n")
            .append("Expires: ").append(config.expiresSeconds()).append("\r\n");
        if (authorization != null) {
            result.append("Authorization: ").append(authorization).append("\r\n");
        }
        result.append("Content-Length: 0\r\n\r\n");
        return result.toString();
    }
}
