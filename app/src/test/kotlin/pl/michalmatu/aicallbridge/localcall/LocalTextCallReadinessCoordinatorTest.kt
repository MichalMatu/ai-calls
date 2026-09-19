package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class LocalTextCallReadinessCoordinatorTest {
    @Test
    fun `unauthorized target fails closed before speech or model work`() {
        val speech = FakeSpeechPreflight(autoReady = true)
        val backend = FakeBackend(autoResponse = "gotowe")
        val timeout = FakeTimeoutScheduler()
        val coordinator = LocalTextCallReadinessCoordinator(
            workflow = readyWorkflow(),
            targetAuthorization = DialTargetAuthorization { false },
            speechPreflight = speech,
            backendFactory = { backend },
            timeoutScheduler = timeout,
        )
        val listener = RecordingReadinessListener()

        coordinator.prepare(listener)

        assertEquals(LocalTextCallReadinessState.FAILED, coordinator.snapshot().state)
        assertEquals("target_not_authorized", coordinator.snapshot().failureReason)
        assertEquals("target_not_authorized", listener.failureReason)
        assertEquals(0, speech.prepareCalls)
        assertEquals(0, backend.generateCalls)
        assertFalse(backend.closed)
    }

    @Test
    fun `preparation completes speech before identity verified model warmup`() {
        val events = mutableListOf<String>()
        val speech = FakeSpeechPreflight(autoReady = true, events = events)
        val backend = FakeBackend(autoResponse = "gotowe", events = events)
        val coordinator = LocalTextCallReadinessCoordinator(
            workflow = readyWorkflow(),
            targetAuthorization = DialTargetAuthorization { true },
            speechPreflight = speech,
            backendFactory = {
                events += "backend_factory"
                backend
            },
            timeoutScheduler = FakeTimeoutScheduler(),
        )
        val listener = RecordingReadinessListener()

        coordinator.prepare(listener)

        assertEquals(
            listOf("speech_prepare", "speech_ready", "backend_factory", "backend_warmup"),
            events,
        )
        assertEquals(LocalTextCallReadinessState.READY_TO_DIAL, coordinator.snapshot().state)
        assertEquals(null, coordinator.snapshot().failureReason)
        assertNotNull(listener.prepared)
        assertEquals(1, backend.generateCalls)
        assertTrue(backend.lastPrompt.isNotBlank())
        assertEquals(1, speech.closeCalls)
        assertFalse(backend.closed)
    }

    @Test
    fun `readiness timeout cancels pending speech and fails closed`() {
        val speech = FakeSpeechPreflight(autoReady = false)
        val timeout = FakeTimeoutScheduler()
        val coordinator = LocalTextCallReadinessCoordinator(
            workflow = readyWorkflow(),
            targetAuthorization = DialTargetAuthorization { true },
            speechPreflight = speech,
            backendFactory = { FakeBackend(autoResponse = "gotowe") },
            timeoutScheduler = timeout,
        )
        val listener = RecordingReadinessListener()

        coordinator.prepare(listener)
        timeout.fire()

        assertEquals(LocalTextCallReadinessState.FAILED, coordinator.snapshot().state)
        assertEquals("readiness_timeout", coordinator.snapshot().failureReason)
        assertEquals("readiness_timeout", listener.failureReason)
        assertTrue(speech.cancelCalls >= 1)
    }

    @Test
    fun `model warmup failure closes backend and never becomes ready`() {
        val backend = FakeBackend(autoError = "server_identity_mismatch")
        val coordinator = LocalTextCallReadinessCoordinator(
            workflow = readyWorkflow(),
            targetAuthorization = DialTargetAuthorization { true },
            speechPreflight = FakeSpeechPreflight(autoReady = true),
            backendFactory = { backend },
            timeoutScheduler = FakeTimeoutScheduler(),
        )
        val listener = RecordingReadinessListener()

        coordinator.prepare(listener)

        assertEquals(LocalTextCallReadinessState.FAILED, coordinator.snapshot().state)
        assertEquals("backend_warmup_server_identity_mismatch", coordinator.snapshot().failureReason)
        assertEquals(null, listener.prepared)
        assertTrue(backend.closed)
    }

    private fun readyWorkflow(): CallWorkflow {
        val task = CallTask(
            "diagnostic target",
            "diagnostic action",
            "diagnostic service",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        return CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(CallResolvedTarget("diagnostic", "000"))
        }
    }

    private class RecordingReadinessListener : LocalTextCallReadinessCoordinator.Listener {
        var prepared: PreparedLocalTextCall? = null
        var failureReason: String? = null

        override fun onReady(prepared: PreparedLocalTextCall) {
            this.prepared = prepared
        }

        override fun onFailure(reason: String) {
            failureReason = reason
        }
    }

    private class FakeSpeechPreflight(
        private val autoReady: Boolean,
        private val events: MutableList<String>? = null,
    ) : LocalTextCallSpeechPreflight {
        var prepareCalls = 0
        var cancelCalls = 0
        var closeCalls = 0
        private var listener: LocalTextCallSpeechPreflight.Listener? = null

        override fun prepare(listener: LocalTextCallSpeechPreflight.Listener) {
            prepareCalls += 1
            this.listener = listener
            events?.add("speech_prepare")
            if (autoReady) {
                events?.add("speech_ready")
                listener.onReady()
            }
        }

        override fun cancel() {
            cancelCalls += 1
        }

        override fun close() {
            closeCalls += 1
            cancel()
        }
    }

    private class FakeBackend(
        private val autoResponse: String? = null,
        private val autoError: String? = null,
        private val events: MutableList<String>? = null,
    ) : TextCallAgentBackend {
        var generateCalls = 0
        var lastPrompt = ""
        var closed = false

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            lastPrompt = userText
            events?.add("backend_warmup")
            when {
                autoError != null -> listener.onError(autoError)
                autoResponse != null -> listener.onComplete(autoResponse)
            }
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }

    private class FakeTimeoutScheduler : LocalTextCallTimeoutScheduler {
        private var action: (() -> Unit)? = null

        override fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable {
            this.action = action
            return object : AutoCloseable {
                override fun close() {
                    this@FakeTimeoutScheduler.action = null
                }
            }
        }

        fun fire() {
            action?.invoke()
        }
    }
}
