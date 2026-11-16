package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

/** Proves fail-closed checks occur before any DNS query, TLS or SIP bytes. */
public final class S8SipsTlsOptionsProbeTest {
    @Test public void cannotContactAnyHostWithoutExplicitOwnerApproval() {
        var target = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        assertThrows(SecurityException.class, () ->
            S8SipsTlsOptionsProbe.query(target, 1200, false));
        assertThrows(SecurityException.class, () ->
            S8SipsTlsOptionsProbe.query(null, 1200, false));
    }

    @Test public void rejectsUnboundedTimeoutAndForgedEndpointsBeforeDNS() {
        var endpoint = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(endpoint, 0, true));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(endpoint, 99_999, true));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(
                new S8SipsOptionsProtocol.Endpoint("127.0.0.1", 5061), 1200, true));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipsTlsOptionsProbe.query(
                new S8SipsOptionsProtocol.Endpoint("example.org", 80), 1200, true));
    }
    private static final class FakeChannel implements S8SipsTlsOptionsProbe.SecureChannel {
        boolean verified;
        boolean closed;
        boolean handshakeFails;
        boolean badResponse;
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();

        @Override public void verifyPeer() throws IOException {
            if (handshakeFails) throw new SSLHandshakeException("certificate_untrusted");
            verified = true;
        }

        @Override public OutputStream output() {
            assertTrue("SIP must not be written before TLS authentication", verified);
            return sent;
        }

        @Override public InputStream input() {
            assertTrue(verified);
            String request = sent.toString(StandardCharsets.US_ASCII);
            assertTrue("SIPS OPTIONS only", request.startsWith("OPTIONS sips:example.org SIP/2.0\r\n"));
            String callId = header(request, "Call-ID: ");
            String via = header(request, "Via: ");
            String response = "SIP/2.0 200 OK\r\n"
                + "Via: " + via + "\r\n"
                + "Call-ID: " + (badResponse ? "wrong-id" : callId) + "\r\n"
                + "CSeq: 1 OPTIONS\r\n"
                + "Content-Length: 0\r\n\r\n";
            return new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII));
        }

        @Override public void close() { closed = true; }

        private static String header(String message, String prefix) {
            for (String line : message.split("\r\n")) {
                if (line.startsWith(prefix)) return line.substring(prefix.length());
            }
            throw new AssertionError("missing SIP header " + prefix);
        }
    }

    @Test public void authenticatedChannelSendsOptionsAndReceivesCorrelated200() throws Exception {
        var target = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        FakeChannel channel = new FakeChannel();
        AtomicInteger opens = new AtomicInteger();
        var result = S8SipsTlsOptionsProbe.query(target, 1800, true, (endpoint, timeout) -> {
            assertEquals(target, endpoint);
            assertEquals(1800, timeout);
            opens.incrementAndGet();
            return channel;
        });
        assertEquals(1, opens.get());
        assertTrue(channel.verified);
        assertTrue(channel.closed);
        assertEquals(200, result.code());
        assertTrue(result.available());
        assertTrue(channel.sent.size() > 0);
        assertFalse(channel.sent.toString(StandardCharsets.US_ASCII).contains("REGISTER"));
        assertFalse(channel.sent.toString(StandardCharsets.US_ASCII).contains("INVITE"));
    }

    @Test public void failedHandshakeCannotSendSIPAndMustCloseChannel() {
        var target = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        FakeChannel channel = new FakeChannel();
        channel.handshakeFails = true;
        assertThrows(SSLHandshakeException.class, () ->
            S8SipsTlsOptionsProbe.query(target, 1200, true, (e, t) -> channel));
        assertEquals(0, channel.sent.size());
        assertTrue(channel.closed);
        assertFalse(channel.verified);
    }

    @Test public void mismatchedServerTransactionIsRejectedAndClosed() {
        var target = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        FakeChannel channel = new FakeChannel();
        channel.badResponse = true;
        assertThrows(IOException.class, () ->
            S8SipsTlsOptionsProbe.query(target, 1200, true, (e, t) -> channel));
        assertTrue(channel.verified);
        assertTrue(channel.closed);
    }

    @Test public void denialCannotEvenCreateALocalTestSocket() {
        var target = S8SipsOptionsProtocol.parseEndpoint("sips:example.org");
        AtomicInteger creates = new AtomicInteger();
        assertThrows(SecurityException.class, () ->
            S8SipsTlsOptionsProbe.query(target, 1200, false, (e, t) -> {
                creates.incrementAndGet();
                throw new AssertionError("denial must precede network creation");
            }));
        assertEquals(0, creates.get());
    }

}
