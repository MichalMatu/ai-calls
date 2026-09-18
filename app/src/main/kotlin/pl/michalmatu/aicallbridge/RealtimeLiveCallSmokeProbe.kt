package pl.michalmatu.aicallbridge

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import pl.michalmatu.aicallbridge.realtime.OkHttpRealtimeTransportFactory
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialBackendProviderFactory
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeEventTrace
import pl.michalmatu.aicallbridge.realtime.TracingRealtimeTransport
import pl.michalmatu.aicallbridge.session.CallMediaSessionRuntime
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestrator
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

/**
 * ADB/shell-only bounded Realtime probe for an already-active cellular call.
 *
 * This object never dials, answers or hangs up. It owns only the temporary AI/media generation and
 * ends that generation through local TAKE OVER, leaving the cellular call available to the human.
 */
object RealtimeLiveCallSmokeProbe {
    fun interface Callback {
        fun onComplete(result: String)
    }

    @JvmStatic
    fun run(context: Context, callback: Callback) {
        run(context, RealtimeLiveCallSmokeSpec.DEFAULT_DURATION_MS, callback)
    }

    @JvmStatic
    fun run(context: Context, durationMs: Long, callback: Callback) {
        val duration = try {
            RealtimeLiveCallSmokeSpec.validateDurationMs(durationMs)
        } catch (error: Throwable) {
            callback.onComplete(immediateResult("FAIL", "invalid_duration", describe(error)))
            return
        }

        val appContext = context.applicationContext ?: context
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            callback.onComplete(immediateResult("FAIL", "audio_manager_unavailable"))
            return
        }
        if (audioManager.mode != AudioManager.MODE_IN_CALL) {
            callback.onComplete(immediateResult("REFUSED", "cellular_call_not_active"))
            return
        }

        val config = try {
            RealtimeNetworkSmokeConfig.loadAndDelete(
                File(appContext.filesDir, RealtimeNetworkSmokeConfig.FILE_NAME),
            )
        } catch (error: Throwable) {
            callback.onComplete(immediateResult("FAIL", "config_error", describe(error)))
            return
        }

        val credentialProvider = try {
            RealtimeCredentialBackendProviderFactory.create(
                endpoint = config.credentialEndpoint,
                bearerTokenProvider = { config.brokerToken },
            )
        } catch (error: Throwable) {
            callback.onComplete(immediateResult("FAIL", "credential_provider_error", describe(error)))
            return
        }

        Run(appContext, duration, credentialProvider, callback).start()
    }

    private class Run(
        private val context: Context,
        private val durationMs: Long,
        private val credentialProvider: RealtimeCredentialProvider,
        private val callback: Callback,
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)
        private val activeTimerScheduled = AtomicBoolean(false)
        private val tracker = RealtimeLiveCallSmokeStateTracker()
        private val eventTrace = RealtimeEventTrace()
        private var mediaRuntime: CallMediaSessionRuntime? = null
        private var bootstrapExecutor: ExecutorService? = null
        private var orchestrator: CallRealtimeSessionOrchestrator? = null

        private val startupTimeout = Runnable { finish(tracker.timeout()) }
        private val cleanupTimeout = Runnable { finish(tracker.timeout()) }
        private val controlledStop = Runnable {
            if (finished.get()) return@Runnable
            tracker.markControlledStopRequested()
            mainHandler.postDelayed(cleanupTimeout, RealtimeLiveCallSmokeSpec.CLEANUP_TIMEOUT_MS)
            try {
                orchestrator?.takeOverNow()
            } catch (error: Throwable) {
                finish(
                    RealtimeLiveCallSmokeResult(
                        status = "FAIL",
                        reason = "takeover_failed",
                        detail = describe(error),
                        states = tracker.currentStates(),
                    ),
                )
            }
        }

        fun start() {
            try {
                val media = CallMediaSessionRuntime(context, Consumer { })
                mediaRuntime = media
                val executor = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "realtime-live-probe-bootstrap").apply { isDaemon = true }
                }
                bootstrapExecutor = executor
                val transportFactory = OkHttpRealtimeTransportFactory()
                val created = CallRealtimeSessionOrchestrator(
                    coordinator = media.coordinator(),
                    credentialProvider = credentialProvider,
                    transportFactory = {
                        TracingRealtimeTransport(transportFactory.create(), eventTrace)
                    },
                    bootstrapExecutor = executor,
                    listener = ::onSnapshot,
                    functionCallHandler = null,
                    outputApprovalPolicy = RealtimeLiveCallSmokeSpec.outputApprovalPolicy,
                )
                orchestrator = created
                mainHandler.postDelayed(startupTimeout, RealtimeLiveCallSmokeSpec.STARTUP_TIMEOUT_MS)
                created.start(RealtimeLiveCallSmokeSpec.request())
            } catch (error: Throwable) {
                finish(
                    RealtimeLiveCallSmokeResult(
                        status = "FAIL",
                        reason = "launch_failed",
                        detail = describe(error),
                        states = tracker.currentStates(),
                    ),
                )
            }
        }

        private fun onSnapshot(snapshot: CallRealtimeSessionOrchestratorSnapshot) {
            if (finished.get()) return
            val result = tracker.observe(snapshot)
            if (
                snapshot.state == CallRealtimeSessionOrchestratorState.ACTIVE &&
                result == null &&
                activeTimerScheduled.compareAndSet(false, true)
            ) {
                mainHandler.removeCallbacks(startupTimeout)
                mainHandler.postDelayed(controlledStop, durationMs)
            }
            if (result != null) {
                mainHandler.post { finish(result) }
            }
        }

        private fun finish(result: RealtimeLiveCallSmokeResult) {
            if (!finished.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(startupTimeout)
            mainHandler.removeCallbacks(controlledStop)
            mainHandler.removeCallbacks(cleanupTimeout)

            val currentOrchestrator = orchestrator
            orchestrator = null
            if (currentOrchestrator != null) {
                try {
                    currentOrchestrator.takeOverNow()
                } catch (_: Throwable) {
                }
                try {
                    currentOrchestrator.close()
                } catch (_: Throwable) {
                }
            }

            bootstrapExecutor?.shutdownNow()
            bootstrapExecutor = null
            try {
                mediaRuntime?.close()
            } catch (_: Throwable) {
            }
            mediaRuntime = null

            val traced = result.withTrace(eventTrace.renderCompact())
            mainHandler.post { callback.onComplete(traced.render()) }
        }
    }

    private fun immediateResult(status: String, reason: String, detail: String? = null): String =
        RealtimeLiveCallSmokeResult(status, reason, detail, emptyList()).render()

    private fun describe(error: Throwable): String {
        val type = error.javaClass.simpleName
        val message = error.message?.replace('\n', ' ')?.replace('\r', ' ')?.take(160)
        return if (message.isNullOrBlank()) type else "$type:$message"
    }
}
