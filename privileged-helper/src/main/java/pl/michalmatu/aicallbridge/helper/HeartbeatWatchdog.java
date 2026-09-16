package pl.michalmatu.aicallbridge.helper;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * One-shot heartbeat watchdog used by privileged media sessions.
 *
 * <p>Each heartbeat replaces the pending timeout with a new generation. A stale scheduled task
 * cannot fire the callback after a newer heartbeat because the generation is checked while
 * holding the watchdog lock.</p>
 */
public final class HeartbeatWatchdog implements AutoCloseable {
    private final long timeoutMs;
    private final Runnable onTimeout;
    private final ScheduledExecutorService executor;

    private ScheduledFuture<?> pending;
    private long generation;
    private boolean started;
    private boolean closed;
    private boolean fired;

    public HeartbeatWatchdog(long timeoutMs, Runnable onTimeout) {
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }
        this.timeoutMs = timeoutMs;
        this.onTimeout = Objects.requireNonNull(onTimeout, "onTimeout");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "aicall-helper-watchdog");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("watchdog is closed");
        }
        if (started) {
            throw new IllegalStateException("watchdog already started");
        }
        started = true;
        scheduleLocked();
    }

    /** Returns false when the watchdog can no longer be refreshed. */
    public synchronized boolean heartbeat() {
        if (!started || closed || fired) {
            return false;
        }
        scheduleLocked();
        return true;
    }

    public synchronized boolean hasFired() {
        return fired;
    }

    @Override
    public void close() {
        ScheduledFuture<?> toCancel;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            generation++;
            toCancel = pending;
            pending = null;
        }
        if (toCancel != null) {
            toCancel.cancel(false);
        }
        executor.shutdownNow();
    }

    private void scheduleLocked() {
        generation++;
        long token = generation;
        if (pending != null) {
            pending.cancel(false);
        }
        pending = executor.schedule(() -> fire(token), timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void fire(long token) {
        synchronized (this) {
            if (closed || fired || token != generation) {
                return;
            }
            fired = true;
            closed = true;
            pending = null;
        }
        try {
            onTimeout.run();
        } finally {
            executor.shutdown();
        }
    }
}
