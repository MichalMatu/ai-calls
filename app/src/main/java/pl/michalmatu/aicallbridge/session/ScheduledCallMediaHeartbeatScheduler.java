package pl.michalmatu.aicallbridge.session;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Production heartbeat scheduler. The 500 ms cadence stays inside the helper's 2 s watchdog. */
public final class ScheduledCallMediaHeartbeatScheduler
    implements CallMediaHeartbeatScheduler, AutoCloseable {

    public static final long HEARTBEAT_PERIOD_MS = 500L;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
        runnable -> {
            Thread thread = new Thread(runnable, "aicall-media-heartbeat");
            thread.setDaemon(true);
            return thread;
        }
    );

    @Override
    public AutoCloseable start(long generation, Runnable heartbeat) {
        Objects.requireNonNull(heartbeat, "heartbeat");
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            heartbeat,
            0L,
            HEARTBEAT_PERIOD_MS,
            TimeUnit.MILLISECONDS
        );
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
