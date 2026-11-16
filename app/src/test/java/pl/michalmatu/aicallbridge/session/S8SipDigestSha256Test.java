package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class S8SipDigestSha256Test {
    private static final String QOP =
        "Digest realm=\"sip.example.org\", nonce=\"n0nce123456789\","
            + " algorithm=SHA-256, qop=\"auth,auth-int\", charset=UTF-8";
    private static final String CNONCE = "0123456789abcdef0123456789abcdef";
    private static final S8SipsOptionsProtocol.Endpoint ENDPOINT =
        S8SipsOptionsProtocol.parseEndpoint("sips:voice.example.org");

    @Test public void generatesVerifiedSha256RegisterDigestWithoutRetainingPassword() {
        var challenge = S8SipDigestSha256.parseChallenge(QOP);
        char[] password = "s3cret".toCharArray();
        String header = S8SipDigestSha256.registerAuthorization(
            challenge, "alice", password, ENDPOINT, CNONCE
        );
        // Independently computed SHA-256 HA1/HA2/response known answer for this fixture.
        assertEquals(
            "Digest username=\"alice\", realm=\"sip.example.org\","
                + " nonce=\"n0nce123456789\", uri=\"sips:voice.example.org\","
                + " algorithm=SHA-256, qop=auth, nc=00000001,"
                + " cnonce=\"" + CNONCE + "\", response=\""
                + "65cf4062c92e8da9b5546226e6884d30ab35b8a013325a51c5e838dacc4d8c79\"",
            header
        );
        assertArrayEquals("s3cret".toCharArray(), password);
        assertFalse(header.contains("s3cret"));
        assertFalse(header.contains("Authorization:"));
        Arrays.fill(password, '\0');
    }

    @Test public void supportsOpaqueAndExplicitPortWithoutUntrustedHeaderInjection() {
        var challenge = S8SipDigestSha256.parseChallenge(
            "Digest realm=\"sip.example.org\", nonce=\"n0nce123456789\","
                + " algorithm=SHA-256, qop=\"auth\", opaque=\"ticket/123\""
        );
        char[] password = "abc".toCharArray();
        try {
            String header = S8SipDigestSha256.registerAuthorization(
                challenge, "alice", password,
                S8SipsOptionsProtocol.parseEndpoint("sips:voice.example.org:5098"),
                CNONCE
            );
            assertTrue(header.contains("uri=\"sips:voice.example.org:5098\""));
            assertTrue(header.endsWith("opaque=\"ticket/123\""));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Test public void rejectsMd5AbsentAlgorithmAndUnsupportedQopOrExtensions() {
        for (String malicious : List.of(
            QOP.replace("SHA-256", "MD5"),
            QOP.replace("SHA-256", "SHA-256-sess"),
            QOP.replace("algorithm=SHA-256, ", ""),
            QOP.replace("\"auth,auth-int\"", "\"auth-int\""),
            QOP.replace("\"auth,auth-int\"", "\"bogus\""),
            QOP.replace("\"auth,auth-int\"", "\"auth,unknown\""),
            QOP.replace("charset=UTF-8", "charset=ISO-8859-1"),
            QOP + ", stale=true",
            QOP + ", userhash=true",
            QOP + ", algorithm=SHA-256",
            QOP + ", response=\"fake\"",
            QOP + ", nonce=\"other-nonce\"",
            QOP.replace("nonce=\"n0nce123456789\"", "nonce=\"\""),
            QOP.replace("realm=\"sip.example.org\"", "realm=\"\""),
            QOP.replace("Digest ", "Basic "),
            QOP.replace("sip.example.org", "x\r\nAuthorization: evil"),
            QOP + ",",
            QOP.replace("\"auth,auth-int\"", "\"auth,auth-int"),
            QOP.replace("nonce=\"n0nce123456789\"", "nonce=\"x\\\"")
        )) {
            assertThrows("challenge should fail closed", IllegalArgumentException.class,
                () -> S8SipDigestSha256.parseChallenge(malicious));
        }
    }

    @Test public void detectsCredentialHeaderInjectionAndMaliciousChallengeRecord() {
        var challenge = S8SipDigestSha256.parseChallenge(QOP);
        for (String username : List.of(
            "alice\r\nFrom: attacker", "alice:evil", "alice\"\"",
            "alice@example.org", "", " alice", "a".repeat(129)
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> S8SipDigestSha256.registerAuthorization(
                    challenge, username, "x".toCharArray(), ENDPOINT, CNONCE));
        }
        assertThrows(IllegalArgumentException.class, () ->
            S8SipDigestSha256.registerAuthorization(
                new S8SipDigestSha256.Challenge("bad\nrealm", "n0nce123456789", null),
                "alice", "x".toCharArray(), ENDPOINT, CNONCE
            ));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipDigestSha256.registerAuthorization(challenge, "alice", "x".toCharArray(),
                ENDPOINT, "bad\r\nX:evil"));
        assertThrows(IllegalArgumentException.class, () ->
            S8SipDigestSha256.registerAuthorization(challenge, "alice", "x".toCharArray(),
                new S8SipsOptionsProtocol.Endpoint("192.168.1.1", 5061), CNONCE));
    }

    @Test public void cnonceIsFreshAndValidForDigest() {
        String first = S8SipDigestSha256.newCnonce();
        String second = S8SipDigestSha256.newCnonce();
        assertEquals(32, first.length());
        assertTrue(first.matches("[a-f0-9]{32}"));
        assertNotEquals(first, second);
        assertFalse(first.contains(" "));
    }
}
