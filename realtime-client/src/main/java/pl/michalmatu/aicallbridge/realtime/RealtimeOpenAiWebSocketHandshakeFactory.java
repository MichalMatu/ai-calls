package pl.michalmatu.aicallbridge.realtime;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Security boundary for sending a short-lived Realtime credential from the Android client.
 *
 * <p>The caller may carry an endpoint in transport-neutral configuration, but this WebSocket
 * implementation will only attach the ephemeral token to OpenAI's canonical Realtime endpoint.
 * Model selection is encoded by this factory instead of trusting caller-provided query text.</p>
 */
public final class RealtimeOpenAiWebSocketHandshakeFactory {
    private static final String TRUSTED_SCHEME = "wss";
    private static final String TRUSTED_HOST = "api.openai.com";
    private static final String TRUSTED_PATH = "/v1/realtime";

    public RealtimeWebSocketHandshake create(
        RealtimeSessionConfig config,
        long nowEpochSeconds
    ) {
        Objects.requireNonNull(config, "config");
        if (nowEpochSeconds < 0L) {
            throw new IllegalArgumentException("nowEpochSeconds must be >= 0");
        }

        RealtimeClientSecret secret = Objects.requireNonNull(
            config.getClientSecret(),
            "config.clientSecret"
        );
        if (secret.isExpired(nowEpochSeconds)) {
            throw new IllegalStateException("Realtime client secret is expired");
        }
        requireSafeProtocolToken(secret.getValue());
        requireTrustedEndpoint(config.getSessionEndpoint());

        String encodedModel = URLEncoder.encode(config.getModel(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String requestUrl = TRUSTED_SCHEME
            + "://"
            + TRUSTED_HOST
            + TRUSTED_PATH
            + "?model="
            + encodedModel;
        return new RealtimeWebSocketHandshake(
            requestUrl,
            List.of(
                "realtime",
                "openai-insecure-api-key." + secret.getValue()
            )
        );
    }

    private static void requireTrustedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("sessionEndpoint must not be blank");
        }
        final URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("invalid sessionEndpoint", error);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
            || !TRUSTED_SCHEME.equals(scheme.toLowerCase(Locale.ROOT))
            || host == null
            || !TRUSTED_HOST.equals(host.toLowerCase(Locale.ROOT))
            || !TRUSTED_PATH.equals(uri.getRawPath())
            || uri.getUserInfo() != null
            || uri.getRawQuery() != null
            || uri.getRawFragment() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException(
                "sessionEndpoint must be exactly wss://api.openai.com/v1/realtime"
            );
        }
    }

    private static void requireSafeProtocolToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Realtime client secret must not be blank");
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == ',') {
                throw new IllegalArgumentException(
                    "Realtime client secret contains invalid WebSocket protocol characters"
                );
            }
        }
    }
}
