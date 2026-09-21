package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalTextCallSessionFinalTurnRoutingTest {
    @Test
    fun `no plan starts existing pipeline without a route selector`() {
        val workflow = workflow(task(emptyMap()), target())
        val pipeline = FakePipeline()
        val session = LocalTextCallSession(
            PreparedLocalTextCall(workflow, FakeBackend()),
            LocalTextCallSession.PipelineFactory { pipeline },
        )

        session.start(RecordingSpeechListener())

        assertTrue(pipeline.startedWithoutSelector)
        assertNull(pipeline.selector)
        session.close()
    }

    @Test
    fun `plan-bound selector maps say to candidate and structured actions to consumed`() {
        val task = task(mapOf("birth_year" to "1990"))
        val target = target()
        val workflow = workflow(task, target)
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("birth-year", setOf("rok urodzenia"), "birth_year")),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val pipeline = FakePipeline()
        val backend = FakeBackend()
        val session = LocalTextCallSession(
            PreparedLocalTextCall(workflow, backend, plan),
            LocalTextCallSession.PipelineFactory { pipeline },
        )
        val structured = mutableListOf<CallPlanTurnResult>()

        session.start(
            RecordingSpeechListener(),
            LocalTextCallSession.PlanTurnListener { structured += it },
        )

        val selector = checkNotNull(pipeline.selector)
        assertEquals(TextCallFinalTurnRoute.Candidate("1990"), selector.select("rok urodzenia"))
        assertTrue(structured.isEmpty())

        assertSame(TextCallFinalTurnRoute.Consumed, selector.select("nieznane"))
        assertEquals(CallPlanAction.ASK_REPEAT, structured.single().decision().action())

        assertSame(TextCallFinalTurnRoute.Consumed, selector.select("nadal nieznane"))
        assertEquals(CallPlanAction.TAKE_OVER, structured.last().decision().action())
        assertEquals(2, structured.size)
        assertEquals(0, backend.generateCalls)
        session.close()
    }

    @Test(expected = IllegalStateException::class)
    fun `plan-bound session requires a structured result owner`() {
        val task = task(emptyMap())
        val target = target()
        val plan = CallPlan(task, target, emptyList(), CallPlanFallbackPolicy.repeatThenTakeOver(1))
        val session = LocalTextCallSession(
            PreparedLocalTextCall(workflow(task, target), FakeBackend(), plan),
            LocalTextCallSession.PipelineFactory { FakePipeline() },
        )

        session.start(RecordingSpeechListener())
    }

    private fun task(facts: Map<String, String>) = CallTask(
        "Clinic A",
        "book",
        "consultation",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        facts,
    )

    private fun target() = CallResolvedTarget("Clinic A", "+48123456789")

    private fun workflow(task: CallTask, target: CallResolvedTarget): CallWorkflow =
        CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }

    private class FakeBackend : TextCallAgentBackend {
        var generateCalls = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
        }
        override fun cancel() = Unit
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        var startedWithoutSelector = false
        var selector: LocalSpeechFinalTurnRouteSelector? = null

        override fun start(listener: LocalSpeechTextPipeline.Listener) {
            startedWithoutSelector = true
        }

        override fun start(
            listener: LocalSpeechTextPipeline.Listener,
            finalTurnRouteSelector: LocalSpeechFinalTurnRouteSelector,
        ) {
            selector = finalTurnRouteSelector
        }

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class RecordingSpeechListener : LocalSpeechTextPipeline.Listener {
        override fun onSpeechInputReady() = Unit
        override fun onUserTranscript(text: String) = Unit
        override fun onApprovedText(text: String) = Unit
        override fun onOutputPcm16Mono16k(pcm: ByteArray) = Unit
        override fun onDroppedText() = Unit
        override fun onError(reason: String) = error(reason)
    }
}
