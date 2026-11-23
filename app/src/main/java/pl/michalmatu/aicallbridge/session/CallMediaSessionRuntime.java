package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.os.SystemClock;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One coordinator and heartbeat owner; the media transport is replaceable.
 * Only the accepted S8 package can construct the GSM shell media backend.
 */
public final class CallMediaSessionRuntime implements AutoCloseable {
    private final CallMediaSessionBackend backend;
    private final ScheduledCallMediaHeartbeatScheduler heartbeatScheduler;
    private final CallMediaSessionCoordinator coordinator;

    public CallMediaSessionRuntime(
        Context context,
        Consumer<CallMediaSessionSnapshot> listener
    ) {
        this(context, listener, CallMediaBackendMode.forPackage(
            Objects.requireNonNull(context, "context").getPackageName()
        ));
    }

    public CallMediaSessionRuntime(
        Context context,
        Consumer<CallMediaSessionSnapshot> listener,
        CallMediaBackendMode mode
    ) {
        this(createBackend(context, mode), listener);
    }

    /** Backend injection allows focused GSM transport tests without changing call lifecycle ownership. */
    public CallMediaSessionRuntime(
        CallMediaSessionBackend backend,
        Consumer<CallMediaSessionSnapshot> listener
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(listener, "listener");
        heartbeatScheduler = new ScheduledCallMediaHeartbeatScheduler();
        coordinator = new CallMediaSessionCoordinator(
            backend,
            heartbeatScheduler,
            SystemClock::elapsedRealtime,
            listener
        );
    }

    private static CallMediaSessionBackend createBackend(Context context, CallMediaBackendMode mode) {
        Objects.requireNonNull(context, "context");
        if (Objects.requireNonNull(mode, "mode") != CallMediaBackendMode.GSM_SHELL_ADB
            || !GsmShellMediaProtocol.supportedPackage(context.getPackageName())) {
            throw new SecurityException("gsm_media_package_not_allowed");
        }
        return new GsmShellBinderCallMediaSessionBackend();
    }

    public CallMediaSessionCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public void close() {
        coordinator.close();
        heartbeatScheduler.close();
        backend.close();
    }
}
