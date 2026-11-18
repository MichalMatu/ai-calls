package pl.michalmatu.aicallbridge.session;

import java.util.Objects;

/**
 * S8 research adapter: Android's public dialer and microphone APIs do not provide
 * a supported bidirectional PCM endpoint for an ordinary cellular call.
 *
 * Never fake a downlink with microphone audio or route TTS to the speaker and
 * report it as cellular TX. A real backend must replace this adapter only after
 * device-specific RX, TX, STOP and exact-Call tests.
 */
public final class AndroidPublicCallMediaSessionBackend implements CallMediaSessionBackend {
    public static final String BLOCKED_REASON = "cellular_pcm_public_api_unavailable";

    @Override
    public void bind(long generation, BindCallback callback) {
        Objects.requireNonNull(callback, "callback").onBindFailed(
            generation, new UnsupportedOperationException(BLOCKED_REASON)
        );
    }

    @Override
    public void prepare(long generation, int sampleRateHz) {
        throw unavailable();
    }

    @Override
    public CallMediaEndpointLease start(long generation) {
        throw unavailable();
    }

    @Override
    public boolean heartbeat(long generation) {
        return false;
    }

    @Override
    public void abort(long generation) {
        // No media endpoint was opened.
    }

    @Override
    public void unbind(long generation) {
        // No Binder service was bound.
    }

    private static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException(BLOCKED_REASON);
    }
}
