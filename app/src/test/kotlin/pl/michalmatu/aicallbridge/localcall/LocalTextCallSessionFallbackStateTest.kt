package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalTextCallSessionFallbackStateTest {
    @Test
    fun `session owns consecutive unknown count and escalates within configured bound`() {
        val task = task(emptyMap())
        val target = target()
        val workflow = activeWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val backend = FakeBackend()
        val session = session(workflow, plan, backend)

        val first = session.handlePlanFinalTranscript("nieznane")
        val second = session.handlePlanFinalTranscript("nadal nieznane")

        assertEquals(CallPlanAction.ASK_REPEAT, first.decision().action())
        assertEquals(CallPlanAction.TAKE_OVER, second.decision().action())
        assertEquals(0, backend.generateCalls)
        session.close()
    }

    @Test
    fun `recognized deterministic turn resets consecutive unknown count`() {
        val task = task(mapOf("birth_year" to "1990"))
        val target = target()
        val workflow = activeWorkflow(task, target)
        val plan = CallPlan(
            task,
            target,
            listOf(CallPlanRule("birth-year", setOf("rok urodzenia"), "birth_year")),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val backend = FakeBackend()
        val session = session(workflow, plan, backend)

        val firstUnknown = session.handlePlanFinalTranscript("nieznane")
        val known = session.handlePlanFinalTranscript("rok urodzenia")
        val unknownAfterKnown = session.handlePlanFinalTranscript("znowu nieznane")

        assertEquals(CallPlanAction.ASK_REPEAT, firstUnknown.decision().action())
        assertEquals(CallPlanAction.SAY, known.decision().action())
        assertEquals("1990", known.decision().text())
        assertEquals(CallPlanAction.ASK_REPEAT, unknownAfterKnown.decision().action())
        assertEquals(0, backend.generateCalls)
        session.close()
    }

    @Test
    fun `default session consumes unresolved turn while explicit hybrid session generates`() {
        val task = task(emptyMap())
        val target = target()
        val planDefault = CallPlan(task, target, emptyList(), CallPlanFallbackPolicy.takeOverImmediately())
        val planHybrid = CallPlan(task, target, emptyList(), CallPlanFallbackPolicy.takeOverImmediately())
        val defaultSession = session(activeWorkflow(task, target), planDefault, FakeBackend())
        val hybridWorkflow = activeWorkflow(task, target)
        val hybridSession = LocalTextCallSession(
            prepared = PreparedLocalTextCall(hybridWorkflow, FakeBackend(), planHybrid),
            pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
            unresolvedTurnRouting = CallPlanUnresolvedTurnRouting.GENERATE_WITH_BACKEND,
        )

        val defaultSelection = defaultSession.injectSyntheticFinalTranscript("nieznane")
        val hybridSelection = hybridSession.injectSyntheticFinalTranscript("nieznane")

        assertSame(TextCallFinalTurnRoute.Consumed, defaultSelection.route)
        assertSame(TextCallFinalTurnRoute.Generate, hybridSelection.route)
        assertEquals(CallPlanAction.TAKE_OVER, hybridSelection.structuredResult?.decision()?.action())

        defaultSession.close()
        hybridSession.close()
    }

    private fun session(
        workflow: CallWorkflow,
        plan: CallPlan,
        backend: FakeBackend,
    ): LocalTextCallSession = LocalTextCallSession(
        prepared = PreparedLocalTextCall(workflow, backend, plan),
        pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
    )

    private fun task(facts: Map<String, String>) = CallTask(
        "Clinic A",
        "book",
        "consultation",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        facts,
    )

    private fun target() = CallResolvedTarget("Clinic A", "+48123456789")

    private fun activeWorkflow(task: CallTask, target: CallResolvedTarget): CallWorkflow =
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
        override fun start(listener: LocalSpeechTextPipeline.Listener) = Unit
        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
