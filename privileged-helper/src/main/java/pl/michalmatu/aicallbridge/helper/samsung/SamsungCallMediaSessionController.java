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
 *
 * <p>The target S22 firmware has asymmetric attribution requirements. VOICE_DOWNLINK uses the
 * proven system attribution while CALL_ASSISTANT TX uses the proven com.android.shell attribution.
 * The downlink must also be constructed before Context/AudioManager initialization in the direct
 * shell proof path, so callers first invoke {@link #prepare(int)}, then create/obtain the required
 * contexts, then invoke {@link #start(Context, Context)}.</p>
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

    private SamsungDownlinkPipeSession preparedDownlink;
    private ParcelFileDescriptor preparedDownlinkReader;
    private int preparedSampleRate;

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
     * Context-free first phase. Opens VOICE_DOWNLINK and reserves its controller read pipe.
     *
     * <p>On the proven direct-shell S22 path this must happen before constructing the system or
     * shell app Context used by the second phase.</p>
     */
    public synchronized void prepare(int sampleRate) {
        reapTerminatedLocked();
        if (preparedDownlink != null || activeDownlink != null || activeUplink != null) {
            throw new IllegalStateException("a call media session is already prepared or active");
        }

        SamsungDownlinkPipeSession candidate = SamsungDownlinkPipeSession.open(sampleRate);
        ParcelFileDescriptor reader = null;
        try {
            reader = candidate.takeReadEnd();
            preparedDownlink = candidate;
            preparedDownlinkReader = reader;
            preparedSampleRate = sampleRate;
        } catch (RuntimeException | Error error) {
            closeQuietly(reader);
            candidate.abortNow();
            throw error;
        }
    }

    /**
     * Transactionally starts both prepared media directions and returns their controller pipes.
     *
     * @param downlinkContext proven system-attribution Context for VOICE_DOWNLINK route guarding
     * @param uplinkContext proven com.android.shell-attribution Context for CALL_ASSISTANT TX
     */
    public synchronized Endpoints start(Context downlinkContext, Context uplinkContext) throws Exception {
        Objects.requireNonNull(downlinkContext, "downlinkContext");
        Objects.requireNonNull(uplinkContext, "uplinkContext");
        reapTerminatedLocked();
        if (activeDownlink != null || activeUplink != null) {
            throw new IllegalStateException("a bidirectional call media session is already active");
        }
        if (preparedDownlink == null || preparedDownlinkReader == null) {
            throw new IllegalStateException("prepare(sampleRate) must be called before start");
        }

        SamsungDownlinkPipeSession downlink = preparedDownlink;
        ParcelFileDescriptor downlinkReader = preparedDownlinkReader;
        int sampleRate = preparedSampleRate;
        preparedDownlink = null;
        preparedDownlinkReader = null;
        preparedSampleRate = 0;

        SamsungUplinkPipeSession uplink = null;
        ParcelFileDescriptor uplinkWriter = null;
        HeartbeatWatchdog newWatchdog = null;
        try {
            uplink = SamsungUplinkPipeSession.open(uplinkContext, sampleRate);
            uplinkWriter = uplink.takeWriteEnd();

            downlink.start(downlinkContext);
            uplink.start();

            long token = ++sessionGeneration;
            SamsungDownlinkPipeSession expectedDownlink = downlink;
            SamsungUplinkPipeSession expectedUplink = uplink;
            newWatchdog = new HeartbeatWatchdog(
                heartbeatTimeoutMs,
                () -> abortIfCurrent(expectedDownlink, expectedUplink, token)
            );

            activeDownlink = downlink;
            activeUplink = uplink;
            watchdog = newWatchdog;
            newWatchdog.start();

            return new Endpoints(downlinkReader, uplinkWriter);
        } catch (Exception error) {
            rollbackStartLocked(downlink, uplink, downlinkReader, uplinkWriter, newWatchdog);
            throw error;
        } catch (Error error) {
            rollbackStartLocked(downlink, uplink, downlinkReader, uplinkWriter, newWatchdog);
            throw error;
        }
    }

    /** Refreshes the single fail-safe deadline after verifying that both media paths are alive. */
    public synchronized boolean heartbeat() {
        reapTerminatedLocked();
        return watchdog != null && watchdog.heartbeat();
    }

    public synchronized boolean hasPreparedSession() {
        return preparedDownlink != null;
    }

    /** Returns true only while both directions belong to the same active session generation. */
    public synchronized boolean hasActiveSession() {
        reapTerminatedLocked();
        return activeDownlink != null && activeUplink != null;
    }

    /** Immediate human-takeover / controller-death path for prepared or active media. */
    public void abortNow() {
        SamsungDownlinkPipeSession prepared;
        ParcelFileDescriptor preparedReader;
        SamsungDownlinkPipeSession downlink;
        SamsungUplinkPipeSession uplink;
        HeartbeatWatchdog toClose;
        synchronized (this) {
            prepared = preparedDownlink;
            preparedReader = preparedDownlinkReader;
            downlink = activeDownlink;
            uplink = activeUplink;
            toClose = watchdog;

            preparedDownlink = null;
            preparedDownlinkReader = null;
            preparedSampleRate = 0;
            activeDownlink = null;
            activeUplink = null;
            watchdog = null;
            sessionGeneration++;
        }

        if (toClose != null) {
            toClose.close();
        }
        closeQuietly(preparedReader);
        abortQuietly(prepared);
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
     * If either active path has ended, fail safe by detaching the generation and aborting its
     * sibling. The watchdog provides the independent upper bound when the controller disappears.
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

    private void rollbackStartLocked(
        SamsungDownlinkPipeSession downlink,
        SamsungUplinkPipeSession uplink,
        ParcelFileDescriptor downlinkReader,
        ParcelFileDescriptor uplinkWriter,
        HeartbeatWatchdog candidateWatchdog
    ) {
        if (activeDownlink == downlink && activeUplink == uplink) {
            activeDownlink = null;
            activeUplink = null;
            watchdog = null;
            sessionGeneration++;
        }
        if (candidateWatchdog != null) {
            candidateWatchdog.close();
        }
        closeQuietly(downlinkReader);
        closeQuietly(uplinkWriter);
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
