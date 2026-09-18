package pl.michalmatu.aicallbridge.session;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Production app-side lifecycle owner for one privileged call-media generation.
 *
 * <p>No Samsung audio or model/network logic lives here. External backend calls are deliberately
 * made outside the state lock so local TAKE OVER cannot be serialized behind a blocking Binder
 * prepare/start/heartbeat call.</p>
 */
public final class CallMediaSessionCoordinator implements AutoCloseable {
    private final Object lock = new Object();
    private final CallMediaSessionBackend backend;
    private final CallMediaHeartbeatScheduler heartbeatScheduler;
    private final CallMediaMonotonicClock clock;
    private final Consumer<CallMediaSessionSnapshot> listener;

    private long generation;
    private CallMediaSessionState state = CallMediaSessionState.IDLE;
    private CallMediaSessionFailure failure = CallMediaSessionFailure.NONE;
    private String failureDetail;
    private long startedAtMs;
    private long updatedAtMs;
    private long heartbeatCount;
    private CallMediaEndpointLease endpointLease;
    private AutoCloseable heartbeatHandle;

    public CallMediaSessionCoordinator(
        CallMediaSessionBackend backend,
        CallMediaHeartbeatScheduler heartbeatScheduler,
        CallMediaMonotonicClock clock,
        Consumer<CallMediaSessionSnapshot> listener
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.heartbeatScheduler = Objects.requireNonNull(heartbeatScheduler, "heartbeatScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.listener = Objects.requireNonNull(listener, "listener");
        final CallMediaSessionSnapshot initial;
        synchronized (lock) {
            updatedAtMs = clock.nowMs();
            initial = snapshotLocked();
        }
        publish(initial);
    }

    public long start(int sampleRateHz) {
        if (sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be > 0");
        }

        final long expectedGeneration;
        final CallMediaSessionSnapshot bindingSnapshot;
        synchronized (lock) {
            if (state != CallMediaSessionState.IDLE) {
                throw new IllegalStateException("cannot start call media from " + state);
            }
            generation++;
            expectedGeneration = generation;
            startedAtMs = clock.nowMs();
            updatedAtMs = startedAtMs;
            heartbeatCount = 0L;
            failure = CallMediaSessionFailure.NONE;
            failureDetail = null;
            state = CallMediaSessionState.BINDING;
            bindingSnapshot = snapshotLocked();
        }
        publish(bindingSnapshot);

        try {
            backend.bind(expectedGeneration, new CallMediaSessionBackend.BindCallback() {
                @Override
                public void onBound(long callbackGeneration) {
                    handleBound(callbackGeneration, sampleRateHz);
                }

                @Override
                public void onDisconnected(long callbackGeneration) {
                    handleDisconnected(callbackGeneration);
                }

                @Override
                public void onBindFailed(long callbackGeneration, Throwable error) {
                    failGeneration(callbackGeneration, CallMediaSessionFailure.BIND_FAILED, error);
                }
            });
        } catch (Throwable error) {
            failGeneration(expectedGeneration, CallMediaSessionFailure.BIND_FAILED, error);
        }
        return expectedGeneration;
    }

    public CallMediaSessionSnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    /** Immediate human takeover. Local endpoint ownership is dropped before privileged cleanup. */
    public void takeOverNow() {
        final long expectedGeneration;
        final CallMediaSessionSnapshot stoppingSnapshot;
        final CallMediaEndpointLease endpoints;
        final AutoCloseable heartbeat;
        CallMediaSessionSnapshot resetSnapshot = null;

        synchronized (lock) {
            if (state == CallMediaSessionState.IDLE || state == CallMediaSessionState.STOPPING) {
                return;
            }
            if (state == CallMediaSessionState.FAILED) {
                state = CallMediaSessionState.IDLE;
                failure = CallMediaSessionFailure.NONE;
                failureDetail = null;
                updatedAtMs = clock.nowMs();
                resetSnapshot = snapshotLocked();
                expectedGeneration = generation;
                stoppingSnapshot = null;
                endpoints = null;
                heartbeat = null;
            } else {
                expectedGeneration = generation;
                state = CallMediaSessionState.STOPPING;
                updatedAtMs = clock.nowMs();
                stoppingSnapshot = snapshotLocked();
                endpoints = endpointLease;
                endpointLease = null;
                heartbeat = heartbeatHandle;
                heartbeatHandle = null;
            }
        }

        if (resetSnapshot != null) {
            publish(resetSnapshot);
            return;
        }
        publish(stoppingSnapshot);
        closeQuietly(heartbeat);
        closeQuietly(endpoints);
        abortQuietly(expectedGeneration);
        unbindQuietly(expectedGeneration);

        CallMediaSessionSnapshot idleSnapshot = null;
        synchronized (lock) {
            if (isCurrentLocked(expectedGeneration, CallMediaSessionState.STOPPING)) {
                state = CallMediaSessionState.IDLE;
                failure = CallMediaSessionFailure.NONE;
                failureDetail = null;
                updatedAtMs = clock.nowMs();
                idleSnapshot = snapshotLocked();
            }
        }
        publish(idleSnapshot);
    }

    @Override
    public void close() {
        takeOverNow();
    }

    private void handleBound(long callbackGeneration, int sampleRateHz) {
        final CallMediaSessionSnapshot preparingSnapshot;
        synchronized (lock) {
            if (!isCurrentLocked(callbackGeneration, CallMediaSessionState.BINDING)) {
                return;
            }
            state = CallMediaSessionState.PREPARING;
            updatedAtMs = clock.nowMs();
            preparingSnapshot = snapshotLocked();
        }
        publish(preparingSnapshot);

        try {
            backend.prepare(callbackGeneration, sampleRateHz);
        } catch (Throwable error) {
            failGeneration(callbackGeneration, CallMediaSessionFailure.PREPARE_FAILED, error);
            return;
        }

        synchronized (lock) {
            if (!isCurrentLocked(callbackGeneration, CallMediaSessionState.PREPARING)) {
                return;
            }
        }

        final CallMediaEndpointLease candidateEndpoints;
        try {
            candidateEndpoints = Objects.requireNonNull(
                backend.start(callbackGeneration),
                "backend.start returned null endpoint lease"
            );
        } catch (Throwable error) {
            failGeneration(callbackGeneration, CallMediaSessionFailure.START_FAILED, error);
            return;
        }

        final CallMediaSessionSnapshot activeSnapshot;
        synchronized (lock) {
            if (!isCurrentLocked(callbackGeneration, CallMediaSessionState.PREPARING)) {
                activeSnapshot = null;
            } else {
                endpointLease = candidateEndpoints;
                state = CallMediaSessionState.ACTIVE;
                updatedAtMs = clock.nowMs();
                activeSnapshot = snapshotLocked();
            }
        }
        if (activeSnapshot == null) {
            closeQuietly(candidateEndpoints);
            // start() may have completed after TAKE OVER already sent an abort; abort again so the
            // stale remote generation cannot survive that race.
            abortQuietly(callbackGeneration);
            unbindQuietly(callbackGeneration);
            return;
        }
        publish(activeSnapshot);

        final AutoCloseable candidateHeartbeat;
        try {
            candidateHeartbeat = Objects.requireNonNull(
                heartbeatScheduler.start(
                    callbackGeneration,
                    () -> runHeartbeat(callbackGeneration)
                ),
                "heartbeat scheduler returned null handle"
            );
        } catch (Throwable error) {
            failGeneration(callbackGeneration, CallMediaSessionFailure.INTERNAL_ERROR, error);
            return;
        }

        boolean keepHeartbeat;
        synchronized (lock) {
            keepHeartbeat = isCurrentLocked(callbackGeneration, CallMediaSessionState.ACTIVE);
            if (keepHeartbeat) {
                heartbeatHandle = candidateHeartbeat;
            }
        }
        if (!keepHeartbeat) {
            closeQuietly(candidateHeartbeat);
        }
    }

    private void handleDisconnected(long callbackGeneration) {
        synchronized (lock) {
            if (callbackGeneration != generation
                || state == CallMediaSessionState.IDLE
                || state == CallMediaSessionState.STOPPING
                || state == CallMediaSessionState.FAILED) {
                return;
            }
        }
        failGeneration(callbackGeneration, CallMediaSessionFailure.HELPER_DISCONNECTED, null);
    }

    private void runHeartbeat(long callbackGeneration) {
        synchronized (lock) {
            if (!isCurrentLocked(callbackGeneration, CallMediaSessionState.ACTIVE)) {
                return;
            }
        }

        final boolean healthy;
        try {
            healthy = backend.heartbeat(callbackGeneration);
        } catch (Throwable error) {
            failGeneration(callbackGeneration, CallMediaSessionFailure.HEARTBEAT_FAILED, error);
            return;
        }
        if (!healthy) {
            failGeneration(callbackGeneration, CallMediaSessionFailure.HEARTBEAT_FAILED, null);
            return;
        }

        synchronized (lock) {
            if (!isCurrentLocked(callbackGeneration, CallMediaSessionState.ACTIVE)) {
                return;
            }
            heartbeatCount++;
            updatedAtMs = clock.nowMs();
        }
    }

    private void failGeneration(
        long expectedGeneration,
        CallMediaSessionFailure reason,
        Throwable error
    ) {
        final CallMediaSessionSnapshot stoppingSnapshot;
        final CallMediaEndpointLease endpoints;
        final AutoCloseable heartbeat;

        synchronized (lock) {
            if (expectedGeneration != generation
                || state == CallMediaSessionState.IDLE
                || state == CallMediaSessionState.STOPPING
                || state == CallMediaSessionState.FAILED) {
                return;
            }
            state = CallMediaSessionState.STOPPING;
            failure = Objects.requireNonNull(reason, "reason");
            failureDetail = describe(error);
            updatedAtMs = clock.nowMs();
            stoppingSnapshot = snapshotLocked();
            endpoints = endpointLease;
            endpointLease = null;
            heartbeat = heartbeatHandle;
            heartbeatHandle = null;
        }
        publish(stoppingSnapshot);

        closeQuietly(heartbeat);
        closeQuietly(endpoints);
        abortQuietly(expectedGeneration);
        unbindQuietly(expectedGeneration);

        CallMediaSessionSnapshot failedSnapshot = null;
        synchronized (lock) {
            if (isCurrentLocked(expectedGeneration, CallMediaSessionState.STOPPING)) {
                state = CallMediaSessionState.FAILED;
                updatedAtMs = clock.nowMs();
                failedSnapshot = snapshotLocked();
            }
        }
        publish(failedSnapshot);
    }

    private boolean isCurrentLocked(long expectedGeneration, CallMediaSessionState expectedState) {
        return expectedGeneration == generation && state == expectedState;
    }

    private CallMediaSessionSnapshot snapshotLocked() {
        return new CallMediaSessionSnapshot(
            generation,
            state,
            failure,
            failureDetail,
            startedAtMs,
            updatedAtMs,
            heartbeatCount
        );
    }

    private void publish(CallMediaSessionSnapshot value) {
        if (value == null) {
            return;
        }
        try {
            listener.accept(value);
        } catch (RuntimeException ignored) {
            // UI/telemetry listeners do not own the call-media safety path.
        }
    }

    private void abortQuietly(long expectedGeneration) {
        try {
            backend.abort(expectedGeneration);
        } catch (Throwable ignored) {
            // Endpoint close + helper watchdog remain independent fail-safe paths.
        }
    }

    private void unbindQuietly(long expectedGeneration) {
        try {
            backend.unbind(expectedGeneration);
        } catch (Throwable ignored) {
            // Best-effort transport cleanup after local fail-safe action.
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignored) {
            // Continue fail-safe cleanup.
        }
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName()
            + ":"
            + message.replace('\n', ' ').replace('\r', ' ');
    }
}
