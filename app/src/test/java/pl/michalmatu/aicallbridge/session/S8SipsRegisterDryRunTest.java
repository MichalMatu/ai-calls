package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

/**
 * Deterministic, completely offline registrar emulator:
 * TLS verification gate, REGISTER, 401, Digest retry, matched 200, STOP.
 */
public final class S8SipsRegisterDryRunTest {
    private static final S8SipsRegisterDraft.Config CONFIG =
        new S8SipsRegisterDraft.Config(
            S8SipsOptionsProtocol.parseEndpoint("sips:voice.example.org"),
            "alice",
            S8SipsOptionsProtocol.parseEndpoint("sips:device.example.org:5099"),
            300
        );
    private static final String DIGEST =
        "Digest realm=\"sip.example.org\", nonce=\"n0nce123456789\", "
            + "algorithm=SHA-256, qop=\"auth\", charset=UTF-8";

    private static final class FakeTls implements S8SipsTlsOptionsProbe.SecureChannel {
        enum Mode {
            SUCCESS, TLS_FAIL, FORGED_401, UNSUPPORTED_MD5,
            FORGED_200, WRONG_CONTACT, MISSING_CONTACT, TOO_LONG_EXPIRY,
            REPEATED_401, DUPLICATE_CONTACT, NONZERO_BODY
        }
        final Mode mode;
        final List<String> sent = new ArrayList<>();
        final ArrayDeque<ByteArrayInputStream> replies = new ArrayDeque<>();
        boolean verified;
        boolean closed;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        FakeTls(Mode mode) { this.mode = mode; }

        @Override public void verifyPeer() throws IOException {
            if (mode == Mode.TLS_FAIL) throw new SSLHandshakeException("untrusted_test_peer");
            verified = true;
        }

        @Override public OutputStream output() {
            assertTrue("never transmit SIP before TLS verification", verified);
            return new OutputStream() {
                @Override public void write(int b) { pending.write(b); }
                @Override public void write(byte[] b, int off, int len) {
                    pending.write(b, off, len);
                }
                @Override public void flush() {
                    String request = pending.toString(StandardCharsets.US_ASCII);
                    pending.reset();
                    sent.add(request);
                    replies.add(new ByteArrayInputStream(
                        reply(request, sent.size()).getBytes(StandardCharsets.US_ASCII)
                    ));
                }
            };
        }

        @Override public InputStream input() {
            assertTrue(verified);
            return new InputStream() {
                @Override public int read() {
                    while (!replies.isEmpty()) {
                        int next = replies.peek().read();
                        if (next != -1) return next;
                        replies.remove();
                    }
                    return -1;
                }
            };
        }

        @Override public void close() { closed = true; }

        String reply(String request, int attempt) {
            assertTrue(request.startsWith("REGISTER sips:voice.example.org SIP/2.0\r\n"));
            assertTrue(request.contains("Content-Length: 0\r\n\r\n"));
            assertFalse(request.contains("INVITE "));
            assertFalse(request.contains("RTP/AVP"));
            String via = header(request, "Via: ");
            String to = header(request, "To: ");
            String from = header(request, "From: ");
            String callId = header(request, "Call-ID: ");
            String cseq = header(request, "CSeq: ");
            String contact = header(request, "Contact: ");
            if (attempt == 1) {
                assertFalse(request.contains("Authorization: "));
                assertEquals("1 REGISTER", cseq);
                return "SIP/2.0 401 Unauthorized\r\n"
                    + "Via: " + (mode == Mode.FORGED_401
                        ? via.replace("branch=z9hG4bK", "branch=z9hG4bKWRONG")
                        : via) + "\r\n"
                    + "To: " + to + ";tag=registrar\r\n"
                    + "From: " + from + "\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: " + cseq + "\r\n"
                    + "WWW-Authenticate: " + (mode == Mode.UNSUPPORTED_MD5
                        ? DIGEST.replace("SHA-256", "MD5") : DIGEST) + "\r\n"
                    + "Content-Length: 0\r\n\r\n";
            }
            assertEquals(2, attempt);
            assertTrue("digest only after correlated 401", request.contains("Authorization: Digest "));
            assertEquals("2 REGISTER", cseq);
            String resultContact = (mode == Mode.WRONG_CONTACT)
                ? contact.replace("device.example.org", "other.example.org") : contact;
            String extra = (mode == Mode.MISSING_CONTACT) ? "" :
                "Contact: " + resultContact + ";expires="
                    + (mode == Mode.TOO_LONG_EXPIRY ? "3600" : "180") + "\r\n";
            if (mode == Mode.DUPLICATE_CONTACT) extra +=
                "Contact: " + contact + ";expires=180\r\n";
            if (mode == Mode.REPEATED_401) {
                return "SIP/2.0 401 Unauthorized\r\n"
                    + "Via: " + via + "\r\n"
                    + "To: " + to + "\r\n"
                    + "From: " + from + "\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: " + cseq + "\r\n"
                    + "WWW-Authenticate: " + DIGEST + "\r\n"
                    + "Content-Length: 0\r\n\r\n";
            }
            return "SIP/2.0 200 OK\r\n"
                + "Via: " + (mode == Mode.FORGED_200
                    ? via.replace("branch=z9hG4bK", "branch=z9hG4bKWRONG") : via) + "\r\n"
                + "To: " + to + ";tag=registrar\r\n"
                + "From: " + from + "\r\n"
                + "Call-ID: " + callId + "\r\n"
                + "CSeq: " + cseq + "\r\n"
                + extra
                + "Expires: 180\r\n"
                + "Content-Length: " + (mode == Mode.NONZERO_BODY ? "1" : "0")
                + "\r\n\r\n";
        }

        private static String header(String message, String prefix) {
            for (String line : message.split("\r\n")) {
                if (line.startsWith(prefix)) return line.substring(prefix.length());
            }
            throw new AssertionError("missing expected REGISTER field");
        }
    }

    @Test public void verifiedTestTlsRegistersOneExactBindingOnlyOn200() throws Exception {
        FakeTls fake = new FakeTls(FakeTls.Mode.SUCCESS);
        char[] password = "test-password".toCharArray();
        try {
            var result = S8SipsRegisterDryRun.execute(CONFIG, password, true, () -> fake);
            assertEquals(2, result.requestCount());
            assertEquals(180, result.acceptedExpiresSeconds());
            assertTrue(fake.verified);
            assertTrue(fake.closed);
            assertEquals(2, fake.sent.size());
            assertFalse(fake.sent.get(0).contains("Authorization: "));
            assertTrue(fake.sent.get(1).contains("algorithm=SHA-256, qop=auth"));
            assertFalse(fake.sent.get(1).contains("test-password"));
            assertEquals(FakeTls.header(fake.sent.get(0), "Call-ID: "),
                FakeTls.header(fake.sent.get(1), "Call-ID: "));
            assertNotEquals(FakeTls.header(fake.sent.get(0), "Via: "),
                FakeTls.header(fake.sent.get(1), "Via: "));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test public void forgedResponsesAndRepeated401CloseWithoutSuccess() {
        for (FakeTls.Mode mode : List.of(
            FakeTls.Mode.FORGED_401, FakeTls.Mode.UNSUPPORTED_MD5,
            FakeTls.Mode.FORGED_200, FakeTls.Mode.WRONG_CONTACT,
            FakeTls.Mode.MISSING_CONTACT, FakeTls.Mode.TOO_LONG_EXPIRY,
            FakeTls.Mode.REPEATED_401, FakeTls.Mode.DUPLICATE_CONTACT,
            FakeTls.Mode.NONZERO_BODY
        )) {
            FakeTls fake = new FakeTls(mode);
            char[] secret = "fake-test-secret".toCharArray();
            try {
                assertThrows("mode must reject: " + mode, IOException.class,
                    () -> S8SipsRegisterDryRun.execute(CONFIG, secret, true, () -> fake));
                assertTrue("closed after " + mode, fake.closed);
                assertTrue(fake.verified);
                assertEquals(mode == FakeTls.Mode.FORGED_401
                    || mode == FakeTls.Mode.UNSUPPORTED_MD5 ? 1 : 2,
                    fake.sent.size());
            } finally {
                Arrays.fill(secret, '\0');
            }
        }
    }

    @Test public void tlsHandshakeFailureEmitsZeroRegisterAndCloses() {
        FakeTls fake = new FakeTls(FakeTls.Mode.TLS_FAIL);
        assertThrows(SSLHandshakeException.class,
            () -> S8SipsRegisterDryRun.execute(
                CONFIG, "fake".toCharArray(), true, () -> fake));
        assertTrue(fake.closed);
        assertEquals(0, fake.sent.size());
    }

    @Test public void lackOfApprovalDoesNotEvenOpenAChannel() {
        AtomicInteger opened = new AtomicInteger();
        assertThrows(SecurityException.class,
            () -> S8SipsRegisterDryRun.execute(
                CONFIG, "fake".toCharArray(), false, () -> {
                    opened.incrementAndGet();
                    throw new AssertionError("network or fake channel must remain unopened");
                }));
        assertEquals(0, opened.get());
    }
}
