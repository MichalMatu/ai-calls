package pl.michalmatu.aicallbridge

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallRealtimeAgentRuntime
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialBackendProviderFactory
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot

/**
 * ADB/shell-only physical smoke for the complete credential -> Realtime -> production media path.
 *
 * This probe never dials. It must be launched while the phone is off-call. Reaching STARTING_MEDIA
 * proves that the short-lived credential and Realtime WebSocket/session setup succeeded; the frozen
 * Samsung media path must then reject start because no cellular call is active.
 *
 * Broker endpoint/auth are read once from app-private storage and deleted before network activity.
 */
object RealtimeNetworkOffCallSmokeProbe {
    fun interface Callback {
        fun onComplete(result: String)
    }

    private const val TIMEOUT_MS = 30_000L

    @JvmStatic
    fun run(context: Context, callback: Callback) {
        val appContext = context.applicationContext ?: context
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            callback.onComplete(immediateResult("FAIL", "audio_manager_unavailable"))
            return
        }
        if (audioManager.mode == AudioManager.MODE_IN_CALL) {
            callback.onComplete(immediateResult("REFUSED", "cellular_call_active"))
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

        val workflow = try {
            diagnosticWorkflow()
        } catch (error: Throwable) {
            callback.onComplete(immediateResult("FAIL", "workflow_setup_error", describe(error)))
            return
        }

        Run(appContext, workflow, credentialProvider, callback).start()
    }

    private class Run(
        private val context: Context,
        private val workflow: CallWorkflow,
        private val credentialProvider: RealtimeCredentialProvider,
        private val callback: Callback,
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)
        private val tracker = RealtimeNetworkSmokeStateTracker()
        private val timeout = Runnable { finish(tracker.timeout()) }
        private var runtime: CallRealtimeAgentRuntime? = null

        fun start() {
            try {
                val created = CallRealtimeAgentRuntime.create(
                    context = context,
                    workflow = workflow,
                    credentialProvider = credentialProvider,
                    listener = ::onSnapshot,
                )
                runtime = created
                mainHandler.postDelayed(timeout, TIMEOUT_MS)
                created.start()
            } catch (error: Throwable) {
                finish(
                    RealtimeNetworkSmokeResult(
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
            tracker.observe(snapshot)?.let(::finish)
        }

        private fun finish(result: RealtimeNetworkSmokeResult) {
            if (!finished.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)

            val current = runtime
            runtime = null
            if (current != null) {
                try {
                    current.takeOverNow()
                } catch (_: Throwable) {
                }
                try {
                    current.close()
                } catch (_: Throwable) {
                }
            }

            mainHandler.post { callback.onComplete(result.render()) }
        }
    }

    private fun diagnosticWorkflow(): CallWorkflow {
        val workflow = CallWorkflow(
            CallTask(
                "Realtime connectivity diagnostic",
                "verify realtime connectivity",
                "non-dialing diagnostic session",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                emptyMap(),
            ),
            CallConfirmationPolicy(),
        ) { }
        workflow.resolveTarget(CallResolvedTarget("Realtime diagnostic target", "not-dialed"))
        workflow.markDialing()
        workflow.markCallActive()
        return workflow
    }

    private fun immediateResult(status: String, reason: String, detail: String? = null): String =
        RealtimeNetworkSmokeResult(status, reason, detail, emptyList()).render()

    private fun describe(error: Throwable): String {
        val type = error.javaClass.simpleName
        val message = error.message?.replace('\n', ' ')?.replace('\r', ' ')
        return if (message.isNullOrBlank()) type else "$type:$message"
    }
}
