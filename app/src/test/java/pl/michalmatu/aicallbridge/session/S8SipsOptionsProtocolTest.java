package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public final class S8SipsOptionsProtocolTest {
    private static final String DOMAIN = "sips:voice.example.org:5061";

    private static String response(
        S8SipsOptionsProtocol.Transaction transaction, int status
    ) {
        return "SIP/2.0 " + status + " " + (status == 200 ? "OK" : "Busy Here") + "\r\n"
            + "Via: SIP/2.0/TLS client.invalid;branch=" + transaction.branch()
            + ";rport=5061\r\n"
            + "Call-ID: " + transaction.callId() + "\r\n"
            + "CSeq: 1 OPTIONS\r\n"
            + "Content-Length: 0\r\n\r\n";
    }

    private static S8SipsOptionsProtocol.ResponseStatus parse(
        S8SipsOptionsProtocol.Transaction t, String response
    ) throws IOException {
        return S8SipsOptionsProtocol.readResponse(
            new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII)), t
        );
    }

    @Test public void strictlyParsesSipsHostnameAndDoesNotLeakCredentials() {
        assertEquals(new S8SipsOptionsProtocol.Endpoint("voice.example.org", 5061),
            S8SipsOptionsProtocol.parseEndpoint(DOMAIN));
        assertEquals(new S8SipsOptionsProtocol.Endpoint("voice.example.org", 5081),
            S8SipsOptionsProtocol.parseEndpoint("sips:VOICE.example.ORG:5081"));
        for (String unsupported : List.of(
            "sip:voice.example.org", "http://voice.example.org", "sips:alice@voice.example.org",
            "sips:voice.example.org;transport=tcp", "sips:voice.example.org/path",
            "sips:voice.example.org?x=1", "sips:[2001:db8::1]:5061",
            "sips:127.0.0.1", "sips:localhost", "sips:voice.example.org:0",
            "sips:voice.example.org:22", "sips:voice.example.org:65536",
            "sips:voice.example.org:+5061", "sips:voice.example.org:50x61",
            "sips:voice.example.org:", "sips:.example.org",
            "sips:voice..example.org", "sips:vo_ice.example.org",
            "sips:-voice.example.org", "sips:voice.example-.org",
            "sips:voice.example.org%0d%0aX"
        )) {
            assertThrows("accepted invalid endpoint " + unsupported,
                IllegalArgumentException.class,
                () -> S8SipsOptionsProtocol.parseEndpoint(unsupported));
        }
    }

    @Test public void generatesWellFormedNonDialingOptionsWithFreshTransactionIdentifiers()
        throws Exception {
        var endpoint = S8SipsOptionsProtocol.parseEndpoint(DOMAIN);
        var first = S8SipsOptionsProtocol.newOptions(endpoint);
        var second = S8SipsOptionsProtocol.newOptions(endpoint);
        assertNotEquals(first.branch(), second.branch());
        assertNotEquals(first.callId(), second.callId());
        assertTrue(first.wire().startsWith(
            "OPTIONS sips:voice.example.org SIP/2.0\r\n"));
        assertTrue(first.wire().contains("Via: SIP/2.0/TLS client.invalid;branch="
            + first.branch() + ";rport\r\n"));
        assertTrue(first.wire().contains("Max-Forwards: 0\r\n"));
        assertTrue(first.wire().contains("CSeq: 1 OPTIONS\r\n"));
        assertTrue(first.wire().endsWith("Content-Length: 0\r\n\r\n"));
        assertFalse(first.wire().contains("INVITE"));
        assertFalse(first.wire().contains("REGISTER"));
        assertFalse(first.wire().contains("Authorization"));
        assertFalse(first.wire().contains("RTP/AVP"));
        assertFalse(first.wire().contains("RTP/SAVP"));
        assertThrows(IllegalArgumentException.class,
            () -> S8SipsOptionsProtocol.newOptions(
                new S8SipsOptionsProtocol.Endpoint("127.0.0.1", 5061)));
    }

    @Test public void acceptsOnlyTheMatchedCompletedTransactionAndDoesNotImplyMedia()
        throws Exception {
        var t = S8SipsOptionsProtocol.newOptions(
            S8SipsOptionsProtocol.parseEndpoint(DOMAIN)
        );
        assertEquals(new S8SipsOptionsProtocol.ResponseStatus(200, true),
            parse(t, response(t, 200)));
        assertEquals(new S8SipsOptionsProtocol.ResponseStatus(486, false),
            parse(t, response(t, 486)));
        assertThrows(IOException.class,
            () -> parse(t, response(t, 100)));
    }

    @Test public void failsClosedOnTruncationOversizeAndAmbiguousOrForgedResponses()
        throws Exception {
        var t = S8SipsOptionsProtocol.newOptions(
            S8SipsOptionsProtocol.parseEndpoint(DOMAIN)
        );
        String okay = response(t, 200);
        for (String invalid : List.of(
            okay.substring(0, okay.length() - 4),
            okay.replace("\r\n", "\n"),
            okay.replace("Call-ID: " + t.callId(), "Call-ID: different"),
            okay.replace("CSeq: 1 OPTIONS", "CSeq: 1 INVITE"),
            okay.replace("Content-Length: 0", "Content-Length: 2"),
            okay.replace("Content-Length: 0", "Content-Length: +0"),
            okay.replace("Content-Length: 0", "Content-Length: 0\r\nContent-Length: 0"),
            okay.replace("branch=" + t.branch(), "branch=" + t.branch() + "EXTRA"),
            okay.replace("SIP/2.0/TLS client.invalid", "SIP/2.0/UDP client.invalid"),
            okay.replace("SIP/2.0 200 OK", "HTTP/1.1 200 OK"),
            okay.replace("SIP/2.0 200 OK", "SIP/2.0 20X OK"),
            okay.replace("CSeq: 1 OPTIONS", " CSeq: 1 OPTIONS"),
            okay.replace("Call-ID: " + t.callId(), "Call-ID: " + t.callId() + "\r\nCall-ID: " + t.callId()),
            okay + "x",
            "SIP/2.0 200 " + "a".repeat(9000) + "\r\n\r\n"
        )) {
            if (invalid.equals(okay + "x")) continue; // Beyond Content-Length: 0 is another message.
            assertThrows(IOException.class, () -> parse(t, invalid));
        }
    }
}
