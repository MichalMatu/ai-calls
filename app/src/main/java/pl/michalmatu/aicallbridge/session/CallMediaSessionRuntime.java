package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.os.SystemClock;

import java.util.Objects;
import java.util.function.Consumer;

/** Small production wiring owner for coordinator + Shizuku backend + heartbeat executor. */
public final class CallMediaSessionRuntime implements AutoCloseable {
    private final ShizukuCallMediaSessionBackend backend;
    private final ScheduledCallMediaHeartbeatScheduler heartbeatScheduler;
    private final CallMediaSessionCoordinator coordinator;

    public CallMediaSessionRuntime(
        Context context,
        Consumer<CallMediaSessionSnapshot> listener
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(listener, "listener");
        backend = new ShizukuCallMediaSessionBackend(context);
        heartbeatScheduler = new ScheduledCallMediaHeartbeatScheduler();
        coordinator = new CallMediaSessionCoordinator(
            backend,
            heartbeatScheduler,
            SystemClock::elapsedRealtime,
            listener
        );
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
