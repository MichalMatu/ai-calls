package pl.michalmatu.aicallbridge.localcall

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

internal enum class LocalTextCallReadinessState {
    IDLE,
    PREPARING,
    READY_TO_DIAL,
    FAILED,
    CLOSED,
}

internal data class LocalTextCallReadinessSnapshot(
    val state: LocalTextCallReadinessState,
    val failureReason: String? = null,
)

internal fun interface DialTargetAuthorization {
    fun isAuthorized(target: CallResolvedTarget): Boolean
}

internal interface LocalTextCallSpeechPreflight : AutoCloseable {
    interface Listener {
        fun onReady()
        fun onError(reason: String)
    }

    fun prepare(listener: Listener)
    fun cancel()
    override fun close() = cancel()
}

internal interface LocalTextCallTimeoutScheduler : AutoCloseable {
    fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable
    override fun close() = Unit
}

internal class ExecutorLocalTextCallTimeoutScheduler : LocalTextCallTimeoutScheduler {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "local-text-call-readiness").apply { isDaemon = true }
    }

    override fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable {
        val future = executor.schedule(action, delayMs, TimeUnit.MILLISECONDS)
        return AutoCloseable { future.cancel(false) }
    }

    override fun close() {
        executor.shutdownNow()
    }
}

/**
 * Product-owned pre-dial gate. It combines the existing workflow authority with local speech
 * readiness and one bounded backend warm-up. The production backend performs runtime startup and
 * exact model identity verification before the warm-up inference, so success here is the only
 * technical READY_TO_DIAL signal used by the local text-call path.
 *
 * An optional CallPlan is binding-only data. It is checked against the exact workflow task and
 * resolved target before speech/model work, then carried into the one-shot prepared session. It
 * does not grant target, commitment, workflow, output or media authority.
 */
internal class LocalTextCallReadinessCoordinator(
    private val workflow: CallWorkflow,
    private val targetAuthorization: DialTargetAuthorization,
    private val speechPreflight: LocalTextCallSpeechPreflight,
    private val backendFactory: () -> TextCallAgentBackend,
    private val timeoutScheduler: LocalTextCallTimeoutScheduler = ExecutorLocalTextCallTimeoutScheduler(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val callPlan: CallPlan? = null,
) : AutoCloseable {
    interface Listener {
        fun onReady(prepared: PreparedLocalTextCall)
        fun onFailure(reason: String)
    }

    private val lock = Any()
    private var state = LocalTextCallReadinessState.IDLE
    private var failureReason: String? = null
    private var timeoutHandle: AutoCloseable? = null
    private var warmingBackend: TextCallAgentBackend? = null
    private var prepared: PreparedLocalTextCall? = null

    init {
        require(timeoutMs > 0L) { "timeout_ms_must_be_positive" }
    }

    fun snapshot(): LocalTextCallReadinessSnapshot = synchronized(lock) {
        LocalTextCallReadinessSnapshot(state, failureReason)
    }

    fun prepare(listener: Listener) {
        synchronized(lock) {
            check(state == LocalTextCallReadinessState.IDLE) { "readiness_already_started" }
            state = LocalTextCallReadinessState.PREPARING
        }

        val workflowSnapshot = workflow.snapshot()
        if (workflowSnapshot.state != CallWorkflowState.READY_TO_DIAL) {
            fail("workflow_not_ready_to_dial", listener)
            return
        }
        val target = workflowSnapshot.resolvedTarget
        if (target == null) {
            fail("target_missing", listener)
            return
        }
        val authorized = try {
            targetAuthorization.isAuthorized(target)
        } catch (_: Throwable) {
            false
        }
        if (!authorized) {
            fail("target_not_authorized", listener)
            return
        }

        val plan = callPlan
        if (plan != null) {
            if (plan.task() !== workflowSnapshot.task) {
                fail("call_plan_task_mismatch", listener)
                return
            }
            if (plan.resolvedTarget() != target) {
                fail("call_plan_target_mismatch", listener)
                return
            }
        }

        try {
            timeoutHandle = timeoutScheduler.schedule(timeoutMs) {
                fail("readiness_timeout", listener)
            }
            speechPreflight.prepare(object : LocalTextCallSpeechPreflight.Listener {
                override fun onReady() = beginBackendWarmup(listener)

                override fun onError(reason: String) {
                    fail("speech_${sanitize(reason)}", listener)
                }
            })
        } catch (error: Throwable) {
            fail("speech_start_${error.javaClass.simpleName}", listener)
        }
    }

    override fun close() {
        val backendToClose: TextCallAgentBackend?
        val preparedToClose: PreparedLocalTextCall?
        val timeoutToClose: AutoCloseable?
        synchronized(lock) {
            if (state == LocalTextCallReadinessState.CLOSED) return
            state = LocalTextCallReadinessState.CLOSED
            backendToClose = warmingBackend
            warmingBackend = null
            preparedToClose = prepared
            prepared = null
            timeoutToClose = timeoutHandle
            timeoutHandle = null
        }
        closeQuietly(timeoutToClose)
        try { speechPreflight.close() } catch (_: Throwable) {}
        try { backendToClose?.close() } catch (_: Throwable) {}
        try { preparedToClose?.close() } catch (_: Throwable) {}
        try { timeoutScheduler.close() } catch (_: Throwable) {}
    }

    private fun beginBackendWarmup(listener: Listener) {
        if (!isPreparing()) return
        try { speechPreflight.close() } catch (_: Throwable) {}

        val backend = try {
            backendFactory()
        } catch (error: Throwable) {
            fail("backend_factory_${error.javaClass.simpleName}", listener)
            return
        }
        synchronized(lock) {
            if (state != LocalTextCallReadinessState.PREPARING) {
                try { backend.close() } catch (_: Throwable) {}
                return
            }
            warmingBackend = backend
        }

        try {
            backend.generate(WARMUP_PROMPT, object : TextCallAgentBackend.Listener {
                override fun onComplete(text: String) {
                    if (text.isBlank()) {
                        fail("backend_warmup_empty_response", listener)
                    } else {
                        completeReady(backend, listener)
                    }
                }

                override fun onError(reason: String) {
                    fail("backend_warmup_${sanitize(reason)}", listener)
                }
            })
        } catch (error: Throwable) {
            fail("backend_warmup_start_${error.javaClass.simpleName}", listener)
        }
    }

    private fun completeReady(backend: TextCallAgentBackend, listener: Listener) {
        val ready: PreparedLocalTextCall
        val timeoutToClose: AutoCloseable?
        synchronized(lock) {
            if (state != LocalTextCallReadinessState.PREPARING || warmingBackend !== backend) return
            ready = PreparedLocalTextCall(workflow, backend, callPlan)
            prepared = ready
            warmingBackend = null
            failureReason = null
            state = LocalTextCallReadinessState.READY_TO_DIAL
            timeoutToClose = timeoutHandle
            timeoutHandle = null
        }
        closeQuietly(timeoutToClose)
        try { timeoutScheduler.close() } catch (_: Throwable) {}
        listener.onReady(ready)
    }

    private fun fail(reason: String, listener: Listener) {
        val backendToClose: TextCallAgentBackend?
        val timeoutToClose: AutoCloseable?
        synchronized(lock) {
            if (state != LocalTextCallReadinessState.PREPARING) return
            state = LocalTextCallReadinessState.FAILED
            failureReason = reason
            backendToClose = warmingBackend
            warmingBackend = null
            timeoutToClose = timeoutHandle
            timeoutHandle = null
        }
        closeQuietly(timeoutToClose)
        try { speechPreflight.cancel() } catch (_: Throwable) {}
        try { backendToClose?.close() } catch (_: Throwable) {}
        try { timeoutScheduler.close() } catch (_: Throwable) {}
        listener.onFailure(reason)
    }

    private fun isPreparing(): Boolean = synchronized(lock) {
        state == LocalTextCallReadinessState.PREPARING
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        try { closeable?.close() } catch (_: Throwable) {}
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').take(160).ifBlank { "unknown" }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
        const val WARMUP_PROMPT = "Odpowiedz jednym słowem: gotowe."
    }
}

/** One-shot ownership transfer from successful readiness into the local text-call session. */
internal class PreparedLocalTextCall internal constructor(
    val workflow: CallWorkflow,
    private val backend: TextCallAgentBackend,
    internal val callPlan: CallPlan? = null,
) : AutoCloseable {
    private val claimed = AtomicBoolean(false)

    internal fun claimBackend(): TextCallAgentBackend {
        check(claimed.compareAndSet(false, true)) { "prepared_local_text_call_already_claimed" }
        return backend
    }

    override fun close() {
        if (claimed.compareAndSet(false, true)) {
            try { backend.close() } catch (_: Throwable) {}
        }
    }
}
