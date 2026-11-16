package pl.michalmatu.aicallbridge.session;

/** Transport behind a single app-owned call generation; never grants Call authority. */
public interface CallMediaSessionBackend extends AutoCloseable {
    interface BindCallback {
        void onBound(long generation);
        void onDisconnected(long generation);
        void onBindFailed(long generation, Throwable error);
    }

    void bind(long generation, BindCallback callback);
    void prepare(long generation, int sampleRateHz);
    CallMediaEndpointLease start(long generation);
    boolean heartbeat(long generation);

    /** Abort must not wait for model inference or network completion. */
    void abort(long generation);
    void unbind(long generation);

    /** Allows non-privileged and fake backends without additional resources. */
    @Override
    default void close() {}
}
