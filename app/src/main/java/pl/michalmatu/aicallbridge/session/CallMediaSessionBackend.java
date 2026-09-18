package pl.michalmatu.aicallbridge.session;

/** Narrow backend seam keeping Shizuku/Binder details out of the lifecycle state machine. */
public interface CallMediaSessionBackend {
    interface BindCallback {
        void onBound(long generation);
        void onDisconnected(long generation);
        void onBindFailed(long generation, Throwable error);
    }

    void bind(long generation, BindCallback callback);
    void prepare(long generation, int sampleRateHz);
    CallMediaEndpointLease start(long generation);
    boolean heartbeat(long generation);

    /** Must dispatch privileged abort without depending on model/network completion. */
    void abort(long generation);

    void unbind(long generation);
}
