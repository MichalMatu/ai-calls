package pl.michalmatu.aicallbridge.helper.samsung;

import android.media.AudioManager;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Fail-safe watcher for the cellular call lifecycle.
 *
 * <p>The proven Samsung media primitives can remain alive briefly after the telephony call itself
 * has ended. This watcher observes the same public signal used before VOICE_DOWNLINK start:
 * {@link AudioManager#MODE_IN_CALL}. Leaving that mode terminates the media session independently
 * of controller heartbeats.</p>
 */
final class CallModeWatchdog implements AutoCloseable {
    private final long pollIntervalMs;
    private final IntSupplier modeSupplier;
    private final Runnable onCallEnded;
    private final ScheduledExecutorService executor;

    private ScheduledFuture<?> pending;
    private boolean started;
    private boolean closed;
    private boolean fired;

    CallModeWatchdog(long pollIntervalMs, IntSupplier modeSupplier, Runnable onCallEnded) {
        if (pollIntervalMs <= 0L) {
            throw new IllegalArgumentException("pollIntervalMs must be > 0");
        }
        this.pollIntervalMs = pollIntervalMs;
        this.modeSupplier = Objects.requireNonNull(modeSupplier, "modeSupplier");
        this.onCallEnded = Objects.requireNonNull(onCallEnded, "onCallEnded");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "aicall-call-mode-watchdog");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    synchronized void start() {
        if (closed) {
            throw new IllegalStateException("watchdog is closed");
        }
        if (started) {
            throw new IllegalStateException("watchdog already started");
        }
        started = true;
        pending = executor.scheduleWithFixedDelay(
            this::poll,
            pollIntervalMs,
            pollIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    synchronized boolean hasFired() {
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
            toCancel = pending;
            pending = null;
        }
        if (toCancel != null) {
            toCancel.cancel(false);
        }
        executor.shutdownNow();
    }

    private void poll() {
        int mode;
        try {
            mode = modeSupplier.getAsInt();
        } catch (Throwable ignored) {
            fire();
            return;
        }
        if (mode != AudioManager.MODE_IN_CALL) {
            fire();
        }
    }

    private void fire() {
        synchronized (this) {
            if (!started || closed || fired) {
                return;
            }
            fired = true;
            closed = true;
            pending = null;
        }
        try {
            onCallEnded.run();
        } finally {
            executor.shutdown();
        }
    }
}
