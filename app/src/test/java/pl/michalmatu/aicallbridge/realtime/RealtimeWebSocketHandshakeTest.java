package pl.michalmatu.aicallbridge.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class RealtimeWebSocketHandshakeTest {
    private final RealtimeOpenAiWebSocketHandshakeFactory factory =
        new RealtimeOpenAiWebSocketHandshakeFactory();

    @Test
    public void buildsOnlyOpenAiRealtimeUrlAndEphemeralSubprotocols() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_short_lived_secret", 2_000L);
        RealtimeSessionConfig config = config("wss://api.openai.com/v1/realtime", secret);

        RealtimeWebSocketHandshake handshake = factory.create(config, 1_999L);

        assertEquals(
            "wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1",
            handshake.requestUrl()
        );
        assertEquals(
            List.of("realtime", "openai-insecure-api-key.eph_short_lived_secret"),
            handshake.protocols()
        );
        assertFalse(handshake.toString().contains("eph_short_lived_secret"));
        assertTrue(handshake.toString().contains("REDACTED"));
    }

    @Test
    public void expiredSecretIsRejectedBeforeHandshake() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_expired", 2_000L);

        assertThrows(
            IllegalStateException.class,
            () -> factory.create(config("wss://api.openai.com/v1/realtime", secret), 2_000L)
        );
    }

    @Test
    public void arbitraryEndpointCannotReceiveClientSecret() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_sensitive", 9_999L);
        List<String> rejected = List.of(
            "ws://api.openai.com/v1/realtime",
            "wss://evil.example/v1/realtime",
            "wss://api.openai.com/v1/other",
            "wss://user@api.openai.com/v1/realtime",
            "wss://api.openai.com:8443/v1/realtime",
            "wss://api.openai.com/v1/realtime?model=attacker-controlled",
            "wss://api.openai.com/v1/realtime#fragment"
        );

        for (String endpoint : rejected) {
            assertThrows(
                "endpoint should be rejected: " + endpoint,
                IllegalArgumentException.class,
                () -> factory.create(config(endpoint, secret), 1L)
            );
        }
    }

    @Test
    public void modelIsEncodedIntoTrustedUrlRatherThanConcatenatedAsRawQuery() {
        RealtimeClientSecret secret = new RealtimeClientSecret("eph_model", 9_999L);
        RealtimeSessionConfig config = new RealtimeSessionConfig(
            "wss://api.openai.com/v1/realtime",
            secret,
            "model/name?unsafe=true",
            "instructions"
        );

        RealtimeWebSocketHandshake handshake = factory.create(config, 1L);

        assertEquals(
            "wss://api.openai.com/v1/realtime?model=model%2Fname%3Funsafe%3Dtrue",
            handshake.requestUrl()
        );
    }

    private static RealtimeSessionConfig config(
        String endpoint,
        RealtimeClientSecret secret
    ) {
        return new RealtimeSessionConfig(
            endpoint,
            secret,
            "gpt-realtime-2.1",
            "Stay inside explicit user authority."
        );
    }
}
