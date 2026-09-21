package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanRule
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

class LocalTextCallPlanBindingTest {
    @Test
    fun `readiness rejects plan bound to another task before speech or backend`() {
        val workflowTask = task(emptyMap())
        val target = CallResolvedTarget("Clinic A", "+48123456789")
        val workflow = readyWorkflow(workflowTask, target)
        val otherTask = task(emptyMap())
        val plan = CallPlan(
            otherTask,
            target,
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val speech = FakeSpeechPreflight()
        val backend = FakeBackend()
        val listener = RecordingReadinessListener()

        LocalTextCallReadinessCoordinator(
            workflow = workflow,
            targetAuthorization = DialTargetAuthorization { true },
            speechPreflight = speech,
            backendFactory = { backend },
            timeoutScheduler = FakeTimeoutScheduler(),
            callPlan = plan,
        ).prepare(listener)

        assertEquals("call_plan_task_mismatch", listener.failureReason)
        assertEquals(0, speech.prepareCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `readiness rejects plan target mismatch before speech or backend`() {
        val task = task(emptyMap())
        val workflowTarget = CallResolvedTarget("Clinic A", "+48123456789")
        val planTarget = CallResolvedTarget("Clinic B", "+48987654321")
        val workflow = readyWorkflow(task, workflowTarget)
        val plan = CallPlan(
            task,
            planTarget,
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val speech = FakeSpeechPreflight()
        val backend = FakeBackend()
        val listener = RecordingReadinessListener()

        LocalTextCallReadinessCoordinator(
            workflow = workflow,
            targetAuthorization = DialTargetAuthorization { true },
            speechPreflight = speech,
            backendFactory = { backend },
            timeoutScheduler = FakeTimeoutScheduler(),
            callPlan = plan,
        ).prepare(listener)

        assertEquals("call_plan_target_mismatch", listener.failureReason)
        assertEquals(0, speech.prepareCalls)
        assertEquals(0, backend.generateCalls)
    }

    @Test
    fun `prepared plan reaches session coordinator without backend fallback`() {
        val task = task(mapOf("birth_year" to "1990"))
        val target = CallResolvedTarget("Clinic A", "+48123456789")
        val workflow = readyWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("birth-year", setOf("rok urodzenia"), "birth_year")),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val backend = FakeBackend(autoResponse = "gotowe")
        val listener = RecordingReadinessListener()
        LocalTextCallReadinessCoordinator(
            workflow = workflow,
            targetAuthorization = DialTargetAuthorization { true },
            speechPreflight = FakeSpeechPreflight(autoReady = true),
            backendFactory = { backend },
            timeoutScheduler = FakeTimeoutScheduler(),
            callPlan = plan,
        ).prepare(listener)

        val prepared = requireNotNull(listener.prepared)
        assertEquals(1, backend.generateCalls)
        workflow.markDialing()
        workflow.markCallActive()
        val session = LocalTextCallSession(
            prepared = prepared,
            pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
        )

        val result = session.handlePlanFinalTranscript("rok urodzenia", 0)

        assertEquals(CallPlanAction.SAY, result.decision().action())
        assertEquals("1990", result.decision().text())
        assertEquals(1, backend.generateCalls)
        session.close()
    }

    @Test
    fun `session without bound plan refuses plan routing`() {
        val backend = FakeBackend()
        val prepared = PreparedLocalTextCall(
            readyWorkflow(task(emptyMap()), CallResolvedTarget("Clinic A", "+48123456789")),
            backend,
        )
        val session = LocalTextCallSession(
            prepared = prepared,
            pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
        )

        assertThrows(IllegalStateException::class.java) {
            session.handlePlanFinalTranscript("anything", 0)
        }
        session.close()
    }

    private fun task(facts: Map<String, String>) = CallTask(
        "Clinic A",
        "book",
        "consultation",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        facts,
    )

    private fun readyWorkflow(task: CallTask, target: CallResolvedTarget) =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply { resolveTarget(target) }

    private class RecordingReadinessListener : LocalTextCallReadinessCoordinator.Listener {
        var prepared: PreparedLocalTextCall? = null
        var failureReason: String? = null
        override fun onReady(prepared: PreparedLocalTextCall) { this.prepared = prepared }
        override fun onFailure(reason: String) { failureReason = reason }
    }

    private class FakeSpeechPreflight(
        private val autoReady: Boolean = false,
    ) : LocalTextCallSpeechPreflight {
        var prepareCalls = 0
        override fun prepare(listener: LocalTextCallSpeechPreflight.Listener) {
            prepareCalls += 1
            if (autoReady) listener.onReady()
        }
        override fun cancel() = Unit
    }

    private class FakeBackend(
        private val autoResponse: String? = null,
    ) : TextCallAgentBackend {
        var generateCalls = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
            autoResponse?.let(listener::onComplete)
        }
        override fun cancel() = Unit
    }

    private class FakeTimeoutScheduler : LocalTextCallTimeoutScheduler {
        override fun schedule(delayMs: Long, action: () -> Unit): AutoCloseable = AutoCloseable { }
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        override fun start(listener: LocalSpeechTextPipeline.Listener) = Unit
        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
