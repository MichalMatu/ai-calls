package pl.michalmatu.aicallbridge.helper.samsung;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.Objects;

import pl.michalmatu.aicallbridge.helper.HeartbeatWatchdog;

/**
 * Helper-side owner for one bidirectional Samsung cellular media session.
 *
 * <p>Control stays local to the privileged helper while PCM travels through two kernel pipes:
 * remote downlink PCM16LE to the controller and controller PCM16LE to the proven
 * CALL_ASSISTANT uplink. One heartbeat watchdog owns the lifetime of both directions. If either
 * media path terminates, or the controller heartbeat expires, both paths are aborted together.</p>
 */
public final class SamsungCallMediaSessionController implements AutoCloseable {
    public static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 2_000L;

    /** Controller-facing pipe endpoints for one successfully started bidirectional session. */
    public static final class Endpoints implements AutoCloseable {
        private ParcelFileDescriptor downlinkReadEnd;
        private ParcelFileDescriptor uplinkWriteEnd;

        private Endpoints(
            ParcelFileDescriptor downlinkReadEnd,
            ParcelFileDescriptor uplinkWriteEnd
        ) {
            this.downlinkReadEnd = Objects.requireNonNull(downlinkReadEnd, "downlinkReadEnd");
            this.uplinkWriteEnd = Objects.requireNonNull(uplinkWriteEnd, "uplinkWriteEnd");
        }

        /** Transfers ownership of the remote-audio PCM16LE read end. May be called once. */
        public synchronized ParcelFileDescriptor takeDownlinkReadEnd() {
            if (downlinkReadEnd == null) {
                throw new IllegalStateException("downlink read end already taken");
            }
            ParcelFileDescriptor result = downlinkReadEnd;
            downlinkReadEnd = null;
            return result;
        }

        /** Transfers ownership of the CALL_ASSISTANT PCM16LE write end. May be called once. */
        public synchronized ParcelFileDescriptor takeUplinkWriteEnd() {
            if (uplinkWriteEnd == null) {
                throw new IllegalStateException("uplink write end already taken");
            }
            ParcelFileDescriptor result = uplinkWriteEnd;
            uplinkWriteEnd = null;
            return result;
        }

        /** Closes any controller endpoint whose ownership has not already been transferred. */
        @Override
        public synchronized void close() {
            closeQuietly(downlinkReadEnd);
            closeQuietly(uplinkWriteEnd);
            downlinkReadEnd = null;
            uplinkWriteEnd = null;
        }
    }

    private final long heartbeatTimeoutMs;

    private SamsungDownlinkPipeSession activeDownlink;
    private SamsungUplinkPipeSession activeUplink;
    private HeartbeatWatchdog watchdog;
    private long sessionGeneration;

    public SamsungCallMediaSessionController() {
        this(DEFAULT_HEARTBEAT_TIMEOUT_MS);
    }

    public SamsungCallMediaSessionController(long heartbeatTimeoutMs) {
        if (heartbeatTimeoutMs <= 0L) {
            throw new IllegalArgumentException("heartbeatTimeoutMs must be > 0");
        }
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
    }

    /**
     * Transactionally starts both media directions and returns their controller-facing pipes.
     * Exactly one bidirectional session may be active at a time.
     *
     * <p>The downlink AudioRecord is opened first so the Samsung VOICE_DOWNLINK path keeps the
     * proven initialization ordering. Endpoints are returned only after both telephony routes have
     * started successfully. Any partial failure closes controller endpoints and aborts both helper
     * media paths.</p>
     */
    public synchronized Endpoints start(Context context, int sampleRate) throws Exception {
        Objects.requireNonNull(context, "context");
        reapTerminatedLocked();
        if (activeDownlink != null || activeUplink != null) {
            throw new IllegalStateException("a bidirectional call media session is already active");
        }

        SamsungDownlinkPipeSession downlink = null;
        SamsungUplinkPipeSession uplink = null;
        ParcelFileDescriptor downlinkReader = null;
        ParcelFileDescriptor uplinkWriter = null;
        try {
            downlink = SamsungDownlinkPipeSession.open(sampleRate);
            downlinkReader = downlink.takeReadEnd();

            uplink = SamsungUplinkPipeSession.open(context, sampleRate);
            uplinkWriter = uplink.takeWriteEnd();

            downlink.start(context);
            uplink.start();

            long token = ++sessionGeneration;
            SamsungDownlinkPipeSession expectedDownlink = downlink;
            SamsungUplinkPipeSession expectedUplink = uplink;
            HeartbeatWatchdog newWatchdog = new HeartbeatWatchdog(
                heartbeatTimeoutMs,
                () -> abortIfCurrent(expectedDownlink, expectedUplink, token)
            );

            activeDownlink = downlink;
            activeUplink = uplink;
            watchdog = newWatchdog;
            newWatchdog.start();

            return new Endpoints(downlinkReader, uplinkWriter);
        } catch (Exception error) {
            closeQuietly(downlinkReader);
            closeQuietly(uplinkWriter);
            abortQuietly(downlink);
            abortQuietly(uplink);
            throw error;
        } catch (Error error) {
            closeQuietly(downlinkReader);
            closeQuietly(uplinkWriter);
            abortQuietly(downlink);
            abortQuietly(uplink);
            throw error;
        }
    }

    /** Refreshes the single fail-safe deadline after verifying that both media paths are alive. */
    public synchronized boolean heartbeat() {
        reapTerminatedLocked();
        return watchdog != null && watchdog.heartbeat();
    }

    /** Returns true only while both directions belong to the same active session generation. */
    public synchronized boolean hasActiveSession() {
        reapTerminatedLocked();
        return activeDownlink != null && activeUplink != null;
    }

    /** Immediate human-takeover / controller-death path for both directions. */
    public void abortNow() {
        SamsungDownlinkPipeSession downlink;
        SamsungUplinkPipeSession uplink;
        HeartbeatWatchdog toClose;
        synchronized (this) {
            downlink = activeDownlink;
            uplink = activeUplink;
            toClose = watchdog;
            activeDownlink = null;
            activeUplink = null;
            watchdog = null;
            sessionGeneration++;
        }

        if (toClose != null) {
            toClose.close();
        }
        abortQuietly(downlink);
        abortQuietly(uplink);
    }

    @Override
    public void close() {
        abortNow();
    }

    private void abortIfCurrent(
        SamsungDownlinkPipeSession expectedDownlink,
        SamsungUplinkPipeSession expectedUplink,
        long token
    ) {
        SamsungDownlinkPipeSession downlink = null;
        SamsungUplinkPipeSession uplink = null;
        synchronized (this) {
            if (
                activeDownlink == expectedDownlink
                    && activeUplink == expectedUplink
                    && token == sessionGeneration
            ) {
                downlink = activeDownlink;
                uplink = activeUplink;
                activeDownlink = null;
                activeUplink = null;
                watchdog = null;
                sessionGeneration++;
            }
        }

        abortQuietly(downlink);
        abortQuietly(uplink);
    }

    /**
     * If either path has ended, fail safe by detaching the generation and aborting its sibling.
     * This is called from every controller interaction; the watchdog provides the independent
     * upper bound when the controller stops interacting entirely.
     */
    private void reapTerminatedLocked() {
        if (activeDownlink == null && activeUplink == null) {
            return;
        }

        boolean downlinkEnded = activeDownlink == null || activeDownlink.isTerminated();
        boolean uplinkEnded = activeUplink == null || activeUplink.isTerminated();
        if (!downlinkEnded && !uplinkEnded) {
            return;
        }

        SamsungDownlinkPipeSession downlink = activeDownlink;
        SamsungUplinkPipeSession uplink = activeUplink;
        HeartbeatWatchdog toClose = watchdog;
        activeDownlink = null;
        activeUplink = null;
        watchdog = null;
        sessionGeneration++;

        if (toClose != null) {
            toClose.close();
        }
        abortQuietly(downlink);
        abortQuietly(uplink);
    }

    private static void abortQuietly(SamsungDownlinkPipeSession session) {
        if (session == null) {
            return;
        }
        try {
            session.abortNow();
        } catch (Throwable ignored) {
            // Fail-safe cleanup path.
        }
    }

    private static void abortQuietly(SamsungUplinkPipeSession session) {
        if (session == null) {
            return;
        }
        try {
            session.abortNow();
        } catch (Throwable ignored) {
            // Fail-safe cleanup path.
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
