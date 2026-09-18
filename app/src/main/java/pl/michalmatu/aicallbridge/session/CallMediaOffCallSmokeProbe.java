package pl.michalmatu.aicallbridge.session;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADB-only diagnostic smoke for the production coordinator + Shizuku backend while no call exists.
 *
 * <p>The expected S22+ path is BINDING -> PREPARING -> STOPPING -> FAILED because the frozen
 * Samsung media primitive refuses to start unless AudioManager is already MODE_IN_CALL. Reaching
 * ACTIVE while off-call is treated as a safety failure and immediately closes the runtime, which
 * invokes local TAKE OVER before reporting the result.</p>
 */
public final class CallMediaOffCallSmokeProbe {
    public interface Callback {
        void onComplete(String result);
    }

    private static final int SAMPLE_RATE_HZ = 16_000;
    private static final long TIMEOUT_MS = 15_000L;
    private static final String EXPECTED_START_FAILURE = "cellular call is not active";

    private CallMediaOffCallSmokeProbe() {}

    public static void run(Context context, Callback callback) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        Callback checkedCallback = Objects.requireNonNull(callback, "callback");
        AudioManager audioManager =
            (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            checkedCallback.onComplete(result("FAIL", "audio_manager_unavailable", null, List.of()));
            return;
        }
        if (audioManager.getMode() == AudioManager.MODE_IN_CALL) {
            checkedCallback.onComplete(result("REFUSED", "cellular_call_active", null, List.of()));
            return;
        }

        new Run(appContext, checkedCallback).start();
    }

    private static final class Run {
        private final Context context;
        private final Callback callback;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final List<CallMediaSessionState> states = new ArrayList<>();
        private final Runnable timeout = () -> finish("FAIL", "timeout", null);

        private CallMediaSessionRuntime runtime;
        private boolean expectedFailureSeen;
        private String expectedFailureDetail;

        Run(Context context, Callback callback) {
            this.context = context;
            this.callback = callback;
        }

        void start() {
            try {
                CallMediaSessionRuntime created = new CallMediaSessionRuntime(context, this::onSnapshot);
                runtime = created;
                mainHandler.postDelayed(timeout, TIMEOUT_MS);
                created.coordinator().start(SAMPLE_RATE_HZ);
            } catch (Throwable error) {
                finish("FAIL", "launch_failed", describe(error));
            }
        }

        private void onSnapshot(CallMediaSessionSnapshot snapshot) {
            if (finished.get()) {
                return;
            }
            if (snapshot.generation() > 0L) {
                appendState(snapshot.state());
            }

            if (snapshot.state() == CallMediaSessionState.ACTIVE) {
                finish("FAIL", "unexpected_active_off_call", snapshot.failureDetail());
                return;
            }

            if (snapshot.state() == CallMediaSessionState.FAILED) {
                String detail = snapshot.failureDetail();
                if (
                    snapshot.failure() != CallMediaSessionFailure.START_FAILED
                        || detail == null
                        || !detail.contains(EXPECTED_START_FAILURE)
                        || !hasState(CallMediaSessionState.PREPARING)
                ) {
                    finish(
                        "FAIL",
                        "unexpected_failure_" + snapshot.failure().name().toLowerCase(),
                        detail
                    );
                    return;
                }

                expectedFailureSeen = true;
                expectedFailureDetail = detail;
                CallMediaSessionRuntime current = runtime;
                if (current == null) {
                    finish("FAIL", "runtime_missing_after_failure", detail);
                    return;
                }
                current.coordinator().takeOverNow();
                return;
            }

            if (
                snapshot.generation() > 0L
                    && snapshot.state() == CallMediaSessionState.IDLE
                    && expectedFailureSeen
            ) {
                finish("PASS", "expected_off_call_start_rejection", expectedFailureDetail);
            }
        }

        private synchronized void appendState(CallMediaSessionState state) {
            if (states.isEmpty() || states.get(states.size() - 1) != state) {
                states.add(state);
            }
        }

        private synchronized boolean hasState(CallMediaSessionState state) {
            return states.contains(state);
        }

        private void finish(String status, String reason, String detail) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            mainHandler.removeCallbacks(timeout);

            CallMediaSessionRuntime current = runtime;
            runtime = null;
            if (current != null) {
                try {
                    current.close();
                } catch (Throwable ignored) {
                    // Reporting must not prevent best-effort teardown from continuing.
                }
            }

            callback.onComplete(result(status, reason, detail, snapshotStates()));
        }

        private synchronized List<CallMediaSessionState> snapshotStates() {
            return List.copyOf(states);
        }
    }

    private static String result(
        String status,
        String reason,
        String detail,
        List<CallMediaSessionState> states
    ) {
        StringBuilder builder = new StringBuilder()
            .append("production_media_off_call_smoke=").append(status)
            .append('\n').append("reason=").append(reason)
            .append('\n').append("states=");
        if (states.isEmpty()) {
            builder.append("none");
        } else {
            for (int index = 0; index < states.size(); index++) {
                if (index > 0) {
                    builder.append('>');
                }
                builder.append(states.get(index).name());
            }
        }
        if (detail != null && !detail.isBlank()) {
            builder.append('\n').append("detail=")
                .append(detail.replace('\n', ' ').replace('\r', ' '));
        }
        return builder.toString();
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
