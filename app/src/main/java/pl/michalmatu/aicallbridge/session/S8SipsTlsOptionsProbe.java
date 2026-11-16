package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * Opt-in, non-dialing one-shot SIP OPTIONS over trusted TLS.
 *
 * Platform CA validation + strict DNS SAN matching; SIP URI SAN-only
 * certificates are intentionally unsupported and fail closed. Full RFC 5922
 * routing/identity resolution, authentication, REGISTER, INVITE and SRTP OPEN.
 * This class never creates a media session or sends SDP/credentials.
 */
public final class S8SipsTlsOptionsProbe {
    public static final int MIN_TIMEOUT_MS = 500;
    public static final int MAX_TIMEOUT_MS = 8000;

    /** Package-private seam for offline, deterministic TLS ordering tests. */
    interface SecureChannel extends AutoCloseable {
        void verifyPeer() throws IOException;
        OutputStream output() throws IOException;
        InputStream input() throws IOException;
        @Override void close() throws IOException;
    }

    interface Connector {
        SecureChannel open(S8SipsOptionsProtocol.Endpoint endpoint, int timeoutMs)
            throws IOException;
    }

    private S8SipsTlsOptionsProbe() {}

    /** Network I/O only after explicit, per-action authorization. Never call on UI thread. */
    public static S8SipsOptionsProtocol.ResponseStatus query(
        S8SipsOptionsProtocol.Endpoint endpoint, int timeoutMs, boolean explicitlyApproved
    ) throws IOException {
        return query(endpoint, timeoutMs, explicitlyApproved,
            S8SipsTlsOptionsProbe::openPlatformChannel);
    }

    static S8SipsOptionsProtocol.ResponseStatus query(
        S8SipsOptionsProtocol.Endpoint endpoint, int timeoutMs,
        boolean explicitlyApproved, Connector connector
    ) throws IOException {
        if (!explicitlyApproved) {
            throw new SecurityException("sips_network_probe_requires_explicit_approval");
        }
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("sips_timeout_out_of_bounds");
        }
        Objects.requireNonNull(endpoint, "endpoint");
        // Revalidate records from external callers before any DNS lookup.
        S8SipsOptionsProtocol.Endpoint checked = S8SipsOptionsProtocol.parseEndpoint(
            "sips:" + endpoint.host() + ":" + endpoint.port()
        );
        S8SipsOptionsProtocol.Transaction transaction =
            S8SipsOptionsProtocol.newOptions(checked);
        try (SecureChannel secure = Objects.requireNonNull(
            connector.open(checked, timeoutMs), "sips_tls_channel_missing"
        )) {
            // No bytes of SIP may leave the app before TLS cert/hostname checks pass.
            secure.verifyPeer();
            OutputStream output = secure.output();
            output.write(transaction.wire().getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return S8SipsOptionsProtocol.readResponse(secure.input(), transaction);
        }
    }

    static SecureChannel openPlatformChannel(
        S8SipsOptionsProtocol.Endpoint endpoint, int timeoutMs
    ) throws IOException {
        Socket tcp = new Socket();
        try {
            tcp.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), timeoutMs);
            tcp.setSoTimeout(timeoutMs);
            // Never write SIP to raw TCP. The only exposed channel is SSL.
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket tls = (SSLSocket) factory.createSocket(
                tcp, endpoint.host(), endpoint.port(), true
            );
            return new SecureChannel() {
                @Override public void verifyPeer() throws IOException {
                    tls.setSoTimeout(timeoutMs);
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    List<String> secureProtocols = new ArrayList<>(2);
                    for (String supported : tls.getSupportedProtocols()) {
                        if (supported.equals("TLSv1.3") || supported.equals("TLSv1.2")) {
                            secureProtocols.add(supported);
                        }
                    }
                    if (secureProtocols.isEmpty()) {
                        throw new IOException("secure_tls_protocol_unavailable");
                    }
                    parameters.setProtocols(secureProtocols.toArray(new String[0]));
                    tls.setSSLParameters(parameters);
                    tls.startHandshake(); // Platform-trusted certificate + DNS identity.
                }
                @Override public OutputStream output() throws IOException {
                    return tls.getOutputStream();
                }
                @Override public InputStream input() throws IOException {
                    return tls.getInputStream();
                }
                @Override public void close() throws IOException { tls.close(); }
            };
        } catch (IOException | RuntimeException error) {
            try { tcp.close(); } catch (IOException ignored) { /* best effort */ }
            throw error;
        }
    }
}
