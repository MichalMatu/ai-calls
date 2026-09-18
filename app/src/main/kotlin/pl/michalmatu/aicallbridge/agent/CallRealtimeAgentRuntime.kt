package pl.michalmatu.aicallbridge.agent

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import pl.michalmatu.aicallbridge.realtime.OkHttpRealtimeTransportFactory
import pl.michalmatu.aicallbridge.realtime.RealtimeCredentialProvider
import pl.michalmatu.aicallbridge.realtime.RealtimeEventTrace
import pl.michalmatu.aicallbridge.realtime.TracingRealtimeTransport
import pl.michalmatu.aicallbridge.session.CallMediaSessionRuntime
import pl.michalmatu.aicallbridge.session.CallMediaSessionSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot

/**
 * Production ownership boundary for one Telephone Agent runtime.
 *
 * The session controller owns the Realtime generation. [mediaOwner] owns the privileged Shizuku
 * backend/coordinator generation. Cleanup always closes the controller first so TAKE OVER and remote
 * cancellation happen before the lower media runtime is destroyed.
 */
class CallRealtimeAgentRuntime(
    private val session: CallRealtimeAgentSession,
    private val bootstrapExecutor: ExecutorService,
    private val mediaOwner: AutoCloseable,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun start(): Long {
        check(!closed.get()) { "Telephone Agent runtime is closed" }
        return session.start()
    }

    fun snapshot(): CallRealtimeSessionOrchestratorSnapshot = session.snapshot()

    fun takeOverNow() {
        if (!closed.get()) {
            session.takeOverNow()
        }
    }

    fun hasPendingUserDecision(): Boolean = session.hasPendingUserDecision()

    fun approvePendingProposal(): Result<CallProposal> {
        if (closed.get()) {
            return Result.failure(IllegalStateException("Telephone Agent runtime is closed"))
        }
        return session.approvePendingProposal()
    }

    fun rejectPendingProposal(): Result<CallProposal> {
        if (closed.get()) {
            return Result.failure(IllegalStateException("Telephone Agent runtime is closed"))
        }
        return session.rejectPendingProposal()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        var firstFailure: Throwable? = null
        try {
            session.close()
        } catch (error: Throwable) {
            firstFailure = error
        }
        try {
            bootstrapExecutor.shutdownNow()
        } catch (error: Throwable) {
            if (firstFailure == null) firstFailure = error else firstFailure.addSuppressed(error)
        }
        try {
            mediaOwner.close()
        } catch (error: Throwable) {
            if (firstFailure == null) firstFailure = error else firstFailure.addSuppressed(error)
        }
        firstFailure?.let { throw it }
    }

    companion object {
        const val DEFAULT_SESSION_ENDPOINT = "wss://api.openai.com/v1/realtime"
        const val DEFAULT_MODEL = "gpt-realtime-2.1"
        private const val DEFAULT_SAMPLE_RATE_HZ = 16_000

        /**
         * Compose the production Shizuku media runtime with a fresh Realtime transport per session.
         * The credential provider remains injected so no server authentication secret is owned here.
         * When [eventTrace] is supplied, the transport records bounded/redacted protocol metadata only.
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            context: Context,
            workflow: CallWorkflow,
            credentialProvider: RealtimeCredentialProvider,
            sessionEndpoint: String = DEFAULT_SESSION_ENDPOINT,
            model: String = DEFAULT_MODEL,
            sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
            listener: (CallRealtimeSessionOrchestratorSnapshot) -> Unit = {},
            mediaListener: (CallMediaSessionSnapshot) -> Unit = {},
            eventTrace: RealtimeEventTrace? = null,
        ): CallRealtimeAgentRuntime {
            val appContext = context.applicationContext ?: context
            val mediaRuntime = CallMediaSessionRuntime(
                appContext,
                Consumer { snapshot -> mediaListener(snapshot) },
            )
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "call-realtime-bootstrap").apply { isDaemon = true }
            }
            return try {
                val transportFactory = OkHttpRealtimeTransportFactory()
                val controller = CallRealtimeAgentSessionController.create(
                    workflow = workflow,
                    coordinator = mediaRuntime.coordinator(),
                    credentialProvider = credentialProvider,
                    transportFactory = {
                        val transport = transportFactory.create()
                        if (eventTrace == null) transport else TracingRealtimeTransport(transport, eventTrace)
                    },
                    bootstrapExecutor = executor,
                    sessionEndpoint = sessionEndpoint,
                    model = model,
                    sampleRateHz = sampleRateHz,
                    listener = listener,
                )
                CallRealtimeAgentRuntime(controller, executor, mediaRuntime)
            } catch (error: Throwable) {
                try {
                    executor.shutdownNow()
                } finally {
                    mediaRuntime.close()
                }
                throw error
            }
        }
    }
}
