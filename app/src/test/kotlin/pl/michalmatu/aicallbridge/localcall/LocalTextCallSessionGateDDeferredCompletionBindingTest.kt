package pl.michalmatu.aicallbridge.localcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanCompletionRule
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyPolicy
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalTextCallSessionGateDDeferredCompletionBindingTest {
    @Test
    fun `reviewed product binding preserves historic workflow completion by default`() {
        val fixture = fixture()

        val selection = fixture.session.injectSyntheticFinalTranscript("gotowe")

        assertSame(TextCallFinalTurnRoute.Consumed, selection.route)
        assertEquals(CallPlanAction.COMPLETE, selection.structuredResult?.decision()?.action())
        assertSame(fixture.outcome, selection.structuredResult?.decision()?.outcome())
        assertEquals(CallWorkflowState.COMPLETED, fixture.workflow.snapshot().state())
        assertSame(fixture.outcome, fixture.workflow.snapshot().outcome())
        fixture.session.close()
    }

    @Test
    fun `explicit reviewed product binding defers completion to product owner`() {
        val fixture = fixture(CallPlanCompletionMode.DEFER_TO_PRODUCT_OWNER)

        val selection = fixture.session.injectSyntheticFinalTranscript("gotowe")

        assertSame(TextCallFinalTurnRoute.Consumed, selection.route)
        assertEquals(CallPlanAction.COMPLETE, selection.structuredResult?.decision()?.action())
        assertSame(fixture.outcome, selection.structuredResult?.decision()?.outcome())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
        assertNull(fixture.workflow.snapshot().outcome())
        fixture.session.close()
    }

    private fun fixture(
        completionMode: CallPlanCompletionMode? = null,
    ): Fixture {
        val task = CallTask(
            "Clinic A",
            "book",
            "consultation",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        val target = CallResolvedTarget("Clinic A", "+48123456789")
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val outcome = CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Done",
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            listOf(CallPlanCompletionRule("done", setOf("gotowe"), outcome)),
            emptyList(),
            CallPlanFallbackPolicy.takeOverImmediately(),
        )
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 0L,
            availableFields = emptySet(),
            authorizedFields = emptySet(),
        )
        val binding = if (completionMode == null) {
            productBinding()
        } else {
            productBinding(completionMode)
        }
        val session = LocalTextCallSession(
            prepared = PreparedLocalTextCall(
                workflow = workflow,
                backend = FakeBackend(),
                callPlan = plan,
                taskGraph = BookAppointmentTaskGraph.definition,
                authorizedFacts = facts,
            ),
            pipelineFactory = LocalTextCallSession.PipelineFactory { FakePipeline() },
            gateDProductBinding = binding,
        )
        return Fixture(workflow, outcome, session)
    }

    private fun productBinding(
        completionMode: CallPlanCompletionMode = CallPlanCompletionMode.APPLY_TO_WORKFLOW,
    ) = LocalTextCallGateDProductBinding(
        deterministicInterpreter = GateDDeterministicCandidateInterpreter { null },
        applyPolicy = TaskGraphApplyPolicy(emptyList()),
        authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { emptySet() },
        applyResultListener = GateDTaskGraphApplyResultListener { },
        callPlanCompletionMode = completionMode,
    )

    private data class Fixture(
        val workflow: CallWorkflow,
        val outcome: CallOutcome,
        val session: LocalTextCallSession,
    )

    private class FakeBackend : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        override fun start(listener: LocalSpeechTextPipeline.Listener) = Unit
        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int) = true
        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
