package pl.michalmatu.aicallbridge.session;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Offline-only integration of REGISTER -> correlated 401 -> SHA-256 retry ->
 * correlated 200. It has NO production socket factory or real-account API.
 *
 * The caller must inject a test channel implementing the verified TLS
 * interface. This exercise does not register an account, contact a SIP
 * provider, prove server access or establish any media / call authorization.
 *
 * Strict behavior: one 401, one retry, one 200, one exact Contact binding,
 * bounded accepted expiry; every unexpected response aborts and closes.
 */
final class S8SipsRegisterDryRun {
    interface TestChannelFactory {
        S8SipsTlsOptionsProbe.SecureChannel open() throws IOException;
    }

    record DryRunResult(int requestCount, int acceptedExpiresSeconds) {}

    private S8SipsRegisterDryRun() {}

    static DryRunResult execute(
        S8SipsRegisterDraft.Config config, char[] password,
        boolean approvedForThisDryRun, TestChannelFactory channels
    ) throws IOException {
        if (!approvedForThisDryRun) {
            throw new SecurityException("sips_register_dry_run_requires_approval");
        }
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(channels, "channels");

        // Validate and allocate one call-id without touching a socket.
        S8SipsRegisterDraft.Draft initial = S8SipsRegisterDraft.initial(config);
        try (S8SipsTlsOptionsProbe.SecureChannel channel =
                 Objects.requireNonNull(channels.open(), "missing_test_tls_channel")) {
            // The test channel controls whether the handshake succeeds; the
            // first REGISTER byte is sent only after explicit TLS verification.
            channel.verifyPeer();
            send(channel.output(), initial.wire());
            S8SipsRegisterDraft.Draft retry = S8SipsRegisterDraft.retryAfter401Response(
                initial, channel.input(), password
            );
            send(channel.output(), retry.wire());
            int expiration = S8SipsRegister200.readAcceptedExpiration(channel.input(), retry);
            return new DryRunResult(2, expiration);
        }
    }

    private static void send(OutputStream output, String message) throws IOException {
        output.write(message.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }
}
