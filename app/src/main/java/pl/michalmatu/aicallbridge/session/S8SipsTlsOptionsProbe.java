package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Opt-in, one-shot SIPS/TLS OPTIONS transport. Not SIP registration or INVITE.
 *
 * Uses Android/JVM default trusted CAs with TLS peer hostname verification.
 * Intentionally supports the DNS SAN subset of SIP server certificates; a
 * certificate relying solely on SIP URI SAN will FAIL rather than fall back
 * to insecure hostname checks. Full RFC 5922 SIP identity routing is OPEN.
 *
 * No HTTP redirects, certificate bypass, Basic/Digest auth, UDP or TCP SIP,
 * plaintext SDP, media, dialer, Shizuku or automatic network scanning.
 * NEVER call from a UI thread. The caller owns explicit user authorization.
 */
public final class S8SipsTlsOptionsProbe {
    public static final int MIN_TIMEOUT_MS = 500;
    public static final int MAX_TIMEOUT_MS = 8000;

    private S8SipsTlsOptionsProbe() {}

    /** Throws on every TLS, response or correlation failure; no fallback. */
    public static S8SipsOptionsProtocol.ResponseStatus query(
        S8SipsOptionsProtocol.Endpoint endpoint, int timeoutMs, boolean explicitlyApproved
    ) throws IOException {
        if (!explicitlyApproved) {
            throw new SecurityException("sips_network_probe_requires_explicit_approval");
        }
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("sips_timeout_out_of_bounds");
        }
        Objects.requireNonNull(endpoint, "endpoint");
        // Revalidate even when the Endpoint record was constructed directly.
        S8SipsOptionsProtocol.Endpoint checked = S8SipsOptionsProtocol.parseEndpoint(
            "sips:" + endpoint.host() + ":" + endpoint.port()
        );
        S8SipsOptionsProtocol.Transaction transaction =
            S8SipsOptionsProtocol.newOptions(checked);

        // The connected raw TCP socket exists only as a carrier for immediate TLS.
        // Never write a single SIP byte until certificate verification completes.
        try (Socket tcp = new Socket()) {
            tcp.connect(new InetSocketAddress(checked.host(), checked.port()), timeoutMs);
            tcp.setSoTimeout(timeoutMs);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) factory.createSocket(
                tcp, checked.host(), checked.port(), false
            )) {
                tls.setSoTimeout(timeoutMs);
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                // Fail closed if platform cannot offer TLS 1.2+.
                List<String> secure = new ArrayList<>(2);
                for (String protocol : tls.getSupportedProtocols()) {
                    if (protocol.equals("TLSv1.3") || protocol.equals("TLSv1.2")) {
                        secure.add(protocol);
                    }
                }
                if (secure.isEmpty()) throw new IOException("secure_tls_protocol_unavailable");
                parameters.setProtocols(secure.toArray(new String[0]));
                tls.setSSLParameters(parameters);
                tls.startHandshake(); // Platform trust and DNS hostname must both pass.
                tls.getOutputStream().write(
                    transaction.wire().getBytes(StandardCharsets.US_ASCII)
                );
                tls.getOutputStream().flush();
                return S8SipsOptionsProtocol.readResponse(tls.getInputStream(), transaction);
            }
        }
    }
}
