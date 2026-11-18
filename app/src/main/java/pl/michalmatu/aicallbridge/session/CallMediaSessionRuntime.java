package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.os.SystemClock;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One coordinator and heartbeat owner; the media transport is replaceable.
 * Stable production package retains SHIZUKU_LEGACY; the isolated S8 lab uses the independent shell Binder GSM backend.
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
        return switch (Objects.requireNonNull(mode, "mode")) {
            case SHIZUKU_LEGACY -> new ShizukuCallMediaSessionBackend(context);
            case GSM_SHELL_ADB -> new GsmShellBinderCallMediaSessionBackend();
            case ANDROID_PUBLIC_API_PROBE -> new AndroidPublicCallMediaSessionBackend();
        };
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
