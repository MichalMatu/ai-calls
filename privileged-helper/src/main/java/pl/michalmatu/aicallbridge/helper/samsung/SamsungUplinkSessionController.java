package pl.michalmatu.aicallbridge.helper.samsung;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.Objects;

import pl.michalmatu.aicallbridge.helper.HeartbeatWatchdog;

/**
 * Helper-side owner for one Samsung uplink media session.
 *
 * <p>This class is intentionally independent of the eventual Binder/Shizuku transport. The
 * privileged service can expose start/heartbeat/abort control calls while PCM continues to travel
 * through the returned kernel pipe. If heartbeats stop, the helper aborts locally without waiting
 * for the ordinary app or network stack.</p>
 */
public final class SamsungUplinkSessionController implements AutoCloseable {
    public static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 2_000L;

    private final long heartbeatTimeoutMs;

    private SamsungUplinkPipeSession activeSession;
    private HeartbeatWatchdog watchdog;
    private long sessionGeneration;

    public SamsungUplinkSessionController() {
        this(DEFAULT_HEARTBEAT_TIMEOUT_MS);
    }

    public SamsungUplinkSessionController(long heartbeatTimeoutMs) {
        if (heartbeatTimeoutMs <= 0L) {
            throw new IllegalArgumentException("heartbeatTimeoutMs must be > 0");
        }
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    /**
     * Opens and starts one uplink session, returning the controller-facing PCM16LE write pipe.
     * Exactly one session may be active at a time.
     */
    public synchronized ParcelFileDescriptor start(Context context, int sampleRate) throws Exception {
        Objects.requireNonNull(context, "context");
        reapTerminatedLocked();
        if (activeSession != null) {
            throw new IllegalStateException("an uplink session is already active");
        }

        SamsungUplinkPipeSession candidate = SamsungUplinkPipeSession.open(context, sampleRate);
        ParcelFileDescriptor writer = null;
        try {
            writer = candidate.takeWriteEnd();
            candidate.start();

            long token = ++sessionGeneration;
            HeartbeatWatchdog newWatchdog = new HeartbeatWatchdog(
                heartbeatTimeoutMs,
                () -> abortIfCurrent(candidate, token)
            );
            activeSession = candidate;
            watchdog = newWatchdog;
            newWatchdog.start();
            return writer;
        } catch (Exception error) {
            closeQuietly(writer);
            candidate.abortNow();
            throw error;
        } catch (Error error) {
            closeQuietly(writer);
            candidate.abortNow();
            throw error;
        }
    }

    /** Refreshes the local fail-safe deadline for the current session. */
    public synchronized boolean heartbeat() {
        reapTerminatedLocked();
        return watchdog != null && watchdog.heartbeat();
    }

    public synchronized boolean hasActiveSession() {
        reapTerminatedLocked();
        return activeSession != null;
    }

    /** Immediate human-takeover / controller-death path. */
    public void abortNow() {
        SamsungUplinkPipeSession toAbort;
        HeartbeatWatchdog toClose;
        synchronized (this) {
            toAbort = activeSession;
            toClose = watchdog;
            activeSession = null;
            watchdog = null;
            sessionGeneration++;
        }
        if (toClose != null) {
            toClose.close();
        }
        if (toAbort != null) {
            toAbort.abortNow();
        }
    }

    @Override
    public void close() {
        abortNow();
    }

    private void abortIfCurrent(SamsungUplinkPipeSession expected, long token) {
        SamsungUplinkPipeSession toAbort = null;
        synchronized (this) {
            if (activeSession == expected && token == sessionGeneration) {
                toAbort = activeSession;
                activeSession = null;
                watchdog = null;
                sessionGeneration++;
            }
        }
        if (toAbort != null) {
            toAbort.abortNow();
        }
    }

    private void reapTerminatedLocked() {
        if (activeSession == null || !activeSession.isTerminated()) {
            return;
        }
        HeartbeatWatchdog oldWatchdog = watchdog;
        activeSession = null;
        watchdog = null;
        sessionGeneration++;
        if (oldWatchdog != null) {
            oldWatchdog.close();
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
            // Failure cleanup path.
        }
    }
}
