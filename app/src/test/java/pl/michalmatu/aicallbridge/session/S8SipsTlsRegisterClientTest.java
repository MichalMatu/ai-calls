package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

/**
 * Isolated fake TLS registrar tests. These never create a DNS query or socket,
 * never register with an operator and never place a voice call.
 */
public final class S8SipsTlsRegisterClientTest {
    private static final S8SipsRegisterDraft.Config CONFIG =
        new S8SipsRegisterDraft.Config(
            S8SipsOptionsProtocol.parseEndpoint("sips:voice.example.org"),
            "alice",
            S8SipsOptionsProtocol.parseEndpoint("sips:device.example.org:5099"),
            300
        );

    @Test public void userApprovedClientReusesVerifiedTlsAndCompletesOneRegistration()
        throws Exception {
        var tls = new S8SipsRegisterDryRunTest.FakeTls(
            S8SipsRegisterDryRunTest.FakeTls.Mode.SUCCESS
        );
        char[] secret = "synthetic-test-only".toCharArray();
        AtomicInteger connections = new AtomicInteger();
        try {
            var result = S8SipsTlsRegisterClient.registerOnce(
                CONFIG, secret, 1800, true, (endpoint, timeout) -> {
                    connections.incrementAndGet();
                    assertEquals(CONFIG.registrar(), endpoint);
                    assertEquals(1800, timeout);
                    return tls;
                }
            );
            assertEquals(180, result.acceptedExpiresSeconds());
            assertEquals(1, connections.get());
            assertTrue(tls.verified);
            assertTrue(tls.closed);
            assertEquals(2, tls.sent.size());
            assertFalse(tls.sent.get(0).contains("Authorization: "));
            assertTrue(tls.sent.get(1).contains("Authorization: Digest "));
            assertFalse(tls.sent.get(1).contains("synthetic-test-only"));
            assertFalse(tls.sent.get(1).contains("INVITE "));
            assertFalse(tls.sent.get(1).contains("RTP/"));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @Test public void unauthorizedRequestsCannotCreateTLSChannelOrReadCredentials() {
        AtomicInteger connections = new AtomicInteger();
        assertThrows(SecurityException.class, () ->
            S8SipsTlsRegisterClient.registerOnce(
                CONFIG, null, 1200, false, (endpoint, timeout) -> {
                    connections.incrementAndGet();
                    throw new AssertionError("must not connect");
                }
            )
        );
        assertEquals(0, connections.get());
    }

    @Test public void invalidSettingsAndSecretsFailBeforeOpeningSocket() {
        AtomicInteger connections = new AtomicInteger();
        S8SipsTlsOptionsProbe.Connector connector = (endpoint, timeout) -> {
            connections.incrementAndGet();
            throw new AssertionError("invalid configuration must not connect");
        };
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsRegisterClient.registerOnce(
                CONFIG, "x".toCharArray(), 100, true, connector
            ));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsRegisterClient.registerOnce(
                CONFIG, "x".toCharArray(), 15000, true, connector
            ));
        assertThrows(NullPointerException.class, () ->
            S8SipsTlsRegisterClient.registerOnce(CONFIG, null, 1200, true, connector));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsRegisterClient.registerOnce(CONFIG, new char[0], 1200, true, connector));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsRegisterClient.registerOnce(
                new S8SipsRegisterDraft.Config(
                    new S8SipsOptionsProtocol.Endpoint("127.0.0.1", 5061),
                    "alice", CONFIG.contact(), 300
                ),
                "x".toCharArray(), 1200, true, connector
            ));
        assertEquals(0, connections.get());
    }

    @Test public void untrustedTlsFailsBeforeRegisterBytesAndCloses() {
        var tls = new S8SipsRegisterDryRunTest.FakeTls(
            S8SipsRegisterDryRunTest.FakeTls.Mode.TLS_FAIL
        );
        char[] secret = "synthetic-test-only".toCharArray();
        try {
            assertThrows(SSLHandshakeException.class, () ->
                S8SipsTlsRegisterClient.registerOnce(
                    CONFIG, secret, 1500, true, (endpoint, timeout) -> tls
                ));
            assertFalse(tls.verified);
            assertTrue(tls.closed);
            assertTrue(tls.sent.isEmpty());
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @Test public void forgedRegistrarOrDowngradeAlwaysClosesWithoutRegistration() {
        for (var mode : List.of(
            S8SipsRegisterDryRunTest.FakeTls.Mode.FORGED_401,
            S8SipsRegisterDryRunTest.FakeTls.Mode.UNSUPPORTED_MD5,
            S8SipsRegisterDryRunTest.FakeTls.Mode.FORGED_200,
            S8SipsRegisterDryRunTest.FakeTls.Mode.DUPLICATE_CONTACT,
            S8SipsRegisterDryRunTest.FakeTls.Mode.REPEATED_401
        )) {
            var tls = new S8SipsRegisterDryRunTest.FakeTls(mode);
            char[] secret = "synthetic-test-only".toCharArray();
            try {
                assertThrows(IOException.class, () ->
                    S8SipsTlsRegisterClient.registerOnce(
                        CONFIG, secret, 1500, true, (endpoint, timeout) -> tls
                    ));
                assertTrue("close TLS after " + mode, tls.closed);
            } finally {
                Arrays.fill(secret, '\0');
            }
        }
    }
    @Test public void cancellationOwnsAndClosesRawSocketWithoutAnySipWrite() throws Exception {
        var attempt = new S8SipsTlsRegisterClient.Attempt();
        Socket first = new Socket();
        assertFalse(first.isClosed());
        attempt.ownSocket(first);
        assertFalse(first.isClosed());
        attempt.close();
        assertTrue(first.isClosed());
        assertTrue(attempt.isStopped());
        Socket late = new Socket();
        try {
            assertThrows(IOException.class, () -> attempt.ownSocket(late));
        } finally {
            late.close();
        }
        assertThrows(IOException.class, () -> attempt.run(
            CONFIG, "test".toCharArray(), 1000, true
        ));
    }

    @Test public void registrationAttemptWithoutApprovalNeverOpensTlsOrConsumesState()
        throws Exception {
        var attempt = new S8SipsTlsRegisterClient.Attempt();
        try {
            assertThrows(SecurityException.class, () -> attempt.run(
                CONFIG, "test".toCharArray(), 1200, false
            ));
            assertFalse(attempt.isStopped());
        } finally {
            attempt.close();
        }
    }

}
