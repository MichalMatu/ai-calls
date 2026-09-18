package pl.michalmatu.aicallbridge.realtime;

import java.util.List;
import java.util.Objects;

/** Validated OpenAI Realtime WebSocket handshake data. */
public final class RealtimeWebSocketHandshake {
    private final String requestUrl;
    private final List<String> protocols;

    RealtimeWebSocketHandshake(String requestUrl, List<String> protocols) {
        this.requestUrl = Objects.requireNonNull(requestUrl, "requestUrl");
        this.protocols = List.copyOf(Objects.requireNonNull(protocols, "protocols"));
        if (this.protocols.isEmpty()) {
            throw new IllegalArgumentException("protocols must not be empty");
        }
    }

    public String requestUrl() {
        return requestUrl;
    }

    /** Includes the short-lived credential and must only be passed to the socket handshake. */
    public List<String> protocols() {
        return protocols;
    }

    @Override
    public String toString() {
        return "RealtimeWebSocketHandshake(requestUrl="
            + requestUrl
            + ", protocols=[realtime, openai-insecure-api-key.REDACTED])";
    }
}
