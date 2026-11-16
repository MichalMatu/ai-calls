package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Offline only: never calls SIP registrar or stores account credentials. */
public final class S8SipsRegisterDraftTest {
    private static final S8SipsRegisterDraft.Config CONFIG =
        new S8SipsRegisterDraft.Config(
            S8SipsOptionsProtocol.parseEndpoint("sips:voice.example.org"),
            "alice",
            S8SipsOptionsProtocol.parseEndpoint("sips:device.example.org:5099"),
            300
        );
    private static final String CHALLENGE =
        "Digest realm=\"sip.example.org\", nonce=\"n0nce123456789\", "
            + "algorithm=SHA-256, qop=\"auth,auth-int\"";

    @Test public void initialRegisterIsBoundedTlsOnlyAndUncredentialed() {
        S8SipsRegisterDraft.Draft initial = S8SipsRegisterDraft.initial(CONFIG);
        String message = initial.wire();
        assertEquals(1, initial.cseq());
        assertTrue(message.startsWith("REGISTER sips:voice.example.org SIP/2.0\r\n"));
        assertTrue(message.contains("Via: SIP/2.0/TLS device.example.org:5099;branch="
            + initial.viaBranch() + ";rport\r\n"));
        assertTrue(message.contains("CSeq: 1 REGISTER\r\n"));
        assertTrue(message.contains("To: <sips:alice@voice.example.org>\r\n"));
        assertTrue(message.contains("Contact: <sips:alice@device.example.org:5099;transport=tls>\r\n"));
        assertTrue(message.contains("Expires: 300\r\n"));
        assertTrue(message.endsWith("Content-Length: 0\r\n\r\n"));
        assertFalse(message.contains("Authorization:"));
        assertFalse(message.contains("INVITE"));
        assertFalse(message.contains("RTP/AVP"));
        assertFalse(message.contains("RTP/SAVP"));
    }

    @Test public void oneValidated401RetryPreservesDialogIdentifiersAndUpdatesBranchCseq() {
        var initial = S8SipsRegisterDraft.initial(CONFIG);
        char[] password = "s3cret".toCharArray();
        try {
            var retry = S8SipsRegisterDraft.retryAfterValidated401(
                initial, S8SipDigestSha256.parseChallenge(CHALLENGE), password
            );
            assertEquals(initial.callId(), retry.callId());
            assertNotEquals(initial.viaBranch(), retry.viaBranch());
            assertEquals(2, retry.cseq());
            assertTrue(retry.wire().contains("CSeq: 2 REGISTER\r\n"));
            assertTrue(retry.wire().contains("Call-ID: " + initial.callId() + "\r\n"));
            assertTrue(retry.wire().contains("Authorization: Digest username=\"alice\""));
            assertTrue(retry.wire().contains("algorithm=SHA-256, qop=auth, nc=00000001"));
            assertFalse(retry.wire().contains("s3cret"));
            assertFalse(retry.wire().contains("Proxy-Authorization:"));
            assertThrows(IllegalStateException.class, () ->
                S8SipsRegisterDraft.retryAfterValidated401(
                    retry, S8SipDigestSha256.parseChallenge(CHALLENGE), password
                ));
            assertEquals(1, initial.cseq());
            assertFalse(initial.wire().contains("Authorization:"));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test public void rejectUntrustedConfigurationBeforeAnyWirePayload() {
        for (String user : List.of(
            "", "alice@example.org", "a\r\nINVITE sips:evil.example",
            "alice:password", " alice", "a".repeat(129)
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> S8SipsRegisterDraft.initial(new S8SipsRegisterDraft.Config(
                    CONFIG.registrar(), user, CONFIG.contact(), 300)));
        }
        for (int expires : List.of(-1, 0, 10, 59, 3601, 50000)) {
            assertThrows(IllegalArgumentException.class,
                () -> S8SipsRegisterDraft.initial(new S8SipsRegisterDraft.Config(
                    CONFIG.registrar(), "alice", CONFIG.contact(), expires)));
        }
        assertThrows(IllegalArgumentException.class,
            () -> S8SipsRegisterDraft.initial(new S8SipsRegisterDraft.Config(
                new S8SipsOptionsProtocol.Endpoint("127.0.0.1", 5061),
                "alice", CONFIG.contact(), 300)));
        assertThrows(IllegalArgumentException.class,
            () -> S8SipsRegisterDraft.initial(new S8SipsRegisterDraft.Config(
                CONFIG.registrar(), "alice",
                new S8SipsOptionsProtocol.Endpoint("127.0.0.1", 5061), 300)));
    }

    @Test public void rejectsWeakServerChallengeAndNeverBuildsAuthenticatedRetry() {
        var first = S8SipsRegisterDraft.initial(CONFIG);
        char[] password = "test".toCharArray();
        try {
            assertThrows(IllegalArgumentException.class,
                () -> S8SipsRegisterDraft.retryAfterValidated401(first,
                    S8SipDigestSha256.parseChallenge(
                        CHALLENGE.replace("SHA-256", "MD5")), password));
            assertThrows(IllegalArgumentException.class,
                () -> S8SipsRegisterDraft.retryAfterValidated401(first,
                    new S8SipDigestSha256.Challenge(
                        "realm\r\nAuthorization: Digest", "n0nce123456789", null), password));
            assertEquals(1, first.cseq());
        } finally {
            Arrays.fill(password, '\0');
        }
    }
    private static String challenge401(S8SipsRegisterDraft.Draft first) {
        return "SIP/2.0 401 Unauthorized\r\n"
            + "Via: SIP/2.0/TLS " + first.viaSentBy() + ";branch="
            + first.viaBranch() + ";rport\r\n"
            + "To: <sips:alice@voice.example.org>;tag=server\r\n"
            + "From: <sips:alice@voice.example.org>;tag=client\r\n"
            + "Call-ID: " + first.callId() + "\r\n"
            + "CSeq: " + first.cseq() + " REGISTER\r\n"
            + "WWW-Authenticate: " + CHALLENGE + "\r\n"
            + "Content-Length: 0\r\n\r\n";
    }

    private static ByteArrayInputStream response(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Test public void complete401AndDigestRetryIsMatchedToPendingRegister() throws Exception {
        var first = S8SipsRegisterDraft.initial(CONFIG);
        char[] password = "s3cret".toCharArray();
        try {
            var retry = S8SipsRegisterDraft.retryAfter401Response(
                first, response(challenge401(first)), password
            );
            assertEquals(2, retry.cseq());
            assertEquals(first.callId(), retry.callId());
            assertNotEquals(first.viaBranch(), retry.viaBranch());
            assertTrue(retry.wire().contains("Authorization: Digest "));
            assertTrue(retry.wire().contains("algorithm=SHA-256, qop=auth"));
            assertFalse(retry.wire().contains("s3cret"));
            assertThrows(IllegalStateException.class, () ->
                S8SipsRegisterDraft.retryAfter401Response(
                    retry, response(challenge401(first)), password
                ));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test public void forged401CannotTriggerDigestAuthorization() {
        var first = S8SipsRegisterDraft.initial(CONFIG);
        String valid = challenge401(first);
        for (String invalid : List.of(
            valid.replace("SIP/2.0 401", "SIP/2.0 407"),
            valid.replace("CSeq: 1 REGISTER", "CSeq: 2 REGISTER"),
            valid.replace("CSeq: 1 REGISTER", "CSeq: 1 INVITE"),
            valid.replace("Call-ID: " + first.callId(), "Call-ID: wrong-id"),
            valid.replace("branch=" + first.viaBranch(), "branch=wrong-branch"),
            valid.replace("branch=" + first.viaBranch(), "branch=" + first.viaBranch() + "BAD"),
            valid.replace("SIP/2.0/TLS " + first.viaSentBy(),
                "SIP/2.0/UDP " + first.viaSentBy()),
            valid.replace("Content-Length: 0", "Content-Length: 1"),
            valid.replace("WWW-Authenticate: " + CHALLENGE,
                "WWW-Authenticate: " + CHALLENGE.replace("SHA-256", "MD5")),
            valid.replace("WWW-Authenticate: " + CHALLENGE + "\r\n", ""),
            valid.replace("Content-Length: 0\r\n",
                "Content-Length: 0\r\nContent-Length: 0\r\n"),
            valid.replace("\r\n", "\\n"),
            valid.replace("CSeq: 1 REGISTER", " CSeq: 1 REGISTER"),
            valid.substring(0, valid.length() - 4)
        )) {
            assertThrows(IOException.class, () ->
                S8SipsRegisterDraft.retryAfter401Response(
                    first, response(invalid), "no-network".toCharArray()
                ));
        }
    }

}
