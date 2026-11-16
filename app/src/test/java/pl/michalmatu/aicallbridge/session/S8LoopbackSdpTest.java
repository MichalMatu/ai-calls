package pl.michalmatu.aicallbridge.session;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class S8LoopbackSdpTest {
    private static final String EXAMPLE = S8LoopbackSdp.offer(17170, 12345L);

    @Test public void offeredPcmuLoopbackNegotiatesExactlyOneTwentyMsTrack() {
        assertTrue(EXAMPLE.contains("m=audio 17170 RTP/AVP 0\r\n"));
        assertTrue(EXAMPLE.contains("a=rtpmap:0 PCMU/8000\r\n"));
        var negotiated = S8LoopbackSdp.acceptLoopbackAnswer(EXAMPLE);
        assertEquals(17170, negotiated.peerRtpPort());
        assertEquals(0, negotiated.payloadType());
        assertEquals(8000, negotiated.clockRateHz());
    }

    @Test public void rejectsInternetEndpointsIpv6DnsAndExternalSourceIps() {
        for (String candidate : List.of(
            EXAMPLE.replace("c=IN IP4 127.0.0.1", "c=IN IP4 192.168.1.10"),
            EXAMPLE.replace("c=IN IP4 127.0.0.1", "c=IN IP4 0.0.0.0"),
            EXAMPLE.replace("c=IN IP4 127.0.0.1", "c=IN IP6 ::1"),
            EXAMPLE.replace("c=IN IP4 127.0.0.1", "c=IN IP4 example.com"),
            EXAMPLE.replace("o=- 12345 1 IN IP4 127.0.0.1",
                "o=- 12345 1 IN IP4 203.0.113.4")
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> S8LoopbackSdp.acceptLoopbackAnswer(candidate));
        }
    }

    @Test public void rejectsOtherProfilesCodecsAndMediaInjection() {
        for (String candidate : List.of(
            EXAMPLE.replace("RTP/AVP 0", "RTP/SAVP 0"),
            EXAMPLE.replace("RTP/AVP 0", "RTP/AVP 0 8"),
            EXAMPLE.replace("RTP/AVP 0", "RTP/AVP 101"),
            EXAMPLE.replace("RTP/AVP 0", "RTP/AVP 8"),
            EXAMPLE.replace("PCMU/8000", "PCMA/8000"),
            EXAMPLE.replace("PCMU/8000", "PCMU/16000"),
            EXAMPLE.replace("a=ptime:20", "a=ptime:10"),
            EXAMPLE.replace("a=sendrecv", "a=recvonly"),
            EXAMPLE.replace("a=sendrecv", "a=inactive"),
            EXAMPLE.replace("a=sendrecv", "a=sendrecv\r\nm=video 19000 RTP/AVP 97"),
            EXAMPLE.replace("a=sendrecv", "a=sendrecv\r\na=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:fake"),
            EXAMPLE.replace("a=sendrecv", "a=sendrecv\r\na=ice-ufrag:anything"),
            EXAMPLE.replace("a=sendrecv", "a=sendrecv\r\na=fingerprint:sha-256 XX")
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> S8LoopbackSdp.acceptLoopbackAnswer(candidate));
        }
    }

    @Test public void refusesPortZeroPrivilegedAndMalformedOrigin() {
        for (String candidate : List.of(
            EXAMPLE.replace("m=audio 17170", "m=audio 0"),
            EXAMPLE.replace("m=audio 17170", "m=audio 80"),
            EXAMPLE.replace("m=audio 17170", "m=audio 65536"),
            EXAMPLE.replace("m=audio 17170", "m=audio +17170"),
            EXAMPLE.replace("m=audio 17170", "m=audio 17170/2"),
            EXAMPLE.replace("o=- 12345", "o=alice 12345"),
            EXAMPLE.replace("o=- 12345", "o=- notANumber"),
            EXAMPLE.replace("o=- 12345", "o=- 12@34")
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> S8LoopbackSdp.acceptLoopbackAnswer(candidate));
        }
        assertThrows(IllegalArgumentException.class, () -> S8LoopbackSdp.offer(80, 1));
        assertThrows(IllegalArgumentException.class, () -> S8LoopbackSdp.offer(16000, 0));
    }

    @Test public void rejectsFramingDuplicatesAndUnknownAttributes() {
        for (String candidate : List.of(
            EXAMPLE.replace("\r\n", "\n"),
            EXAMPLE.substring(0, EXAMPLE.length() - 2),
            EXAMPLE.replace("v=0\r\n", "v=0\r\nv=0\r\n"),
            EXAMPLE.replace("a=ptime:20\r\n", "a=ptime:20\r\na=ptime:20\r\n"),
            EXAMPLE.replace("a=rtpmap:0 PCMU/8000\r\n", ""),
            EXAMPLE.replace("a=sendrecv\r\n", ""),
            EXAMPLE.replace("a=sendrecv\r\n", "a=sendrecv\r\na=sendrecv\r\n"),
            EXAMPLE + "a=sendrecv\r\n",
            EXAMPLE.replace("s=-\r\n", "s=user phone number\r\n"),
            EXAMPLE + "\0"
        )) {
            assertThrows(IllegalArgumentException.class,
                () -> S8LoopbackSdp.acceptLoopbackAnswer(candidate));
        }
    }

    @Test public void doesNotTreatSdpAsSrtpSecurityOrSIPRegistration() {
        var local = S8LoopbackSdp.acceptLoopbackAnswer(EXAMPLE);
        assertEquals(0, local.payloadType());
        // No "secure" option exposed by this API; all remote SRTP/DTLS/SIP is a separate backend.
        assertFalse(EXAMPLE.contains("RTP/SAVP"));
        assertFalse(EXAMPLE.contains("a=crypto:"));
        assertFalse(EXAMPLE.contains("a=fingerprint:"));
    }
}
