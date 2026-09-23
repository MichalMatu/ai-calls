package pl.michalmatu.aicallbridge.localcall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
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

@RunWith(AndroidJUnit4::class)
class AndroidGateDDeferredCompletionBindingContractTest {
    @Test
    fun explicitReviewedBindingDefersSyntheticCompletionWithoutSpeechOrWorkflowMutation() {
        val task = CallTask(
            "Clinic A",
            "book appointment",
            "appointment",
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
            "Appointment confirmed",
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
        val pipeline = RecordingPipeline()
        val backend = RecordingBackend()
        val session = LocalTextCallSession(
            prepared = PreparedLocalTextCall(
                workflow = workflow,
                backend = backend,
                callPlan = plan,
                taskGraph = BookAppointmentTaskGraph.definition,
                authorizedFacts = facts,
            ),
            pipelineFactory = LocalTextCallSession.PipelineFactory { pipeline },
            gateDProductBinding = LocalTextCallGateDProductBinding(
                deterministicInterpreter = GateDDeterministicCandidateInterpreter { null },
                applyPolicy = TaskGraphApplyPolicy(emptyList()),
                authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { emptySet() },
                applyResultListener = GateDTaskGraphApplyResultListener { },
                callPlanCompletionMode = CallPlanCompletionMode.DEFER_TO_PRODUCT_OWNER,
            ),
        )

        try {
            val selection = session.injectSyntheticFinalTranscript("gotowe")

            assertSame(TextCallFinalTurnRoute.Consumed, selection.route)
            assertEquals(CallPlanAction.COMPLETE, selection.structuredResult?.decision()?.action())
            assertSame(outcome, selection.structuredResult?.decision()?.outcome())
            assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, workflow.snapshot().state())
            assertNull(workflow.snapshot().outcome())
            assertEquals(0, pipeline.startCalls)
            assertEquals(0, pipeline.writeCalls)
            assertEquals(0, backend.generateCalls)
        } finally {
            session.close()
        }
    }

    private class RecordingBackend : TextCallAgentBackend {
        var generateCalls = 0

        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class RecordingPipeline : LocalTextCallSession.Pipeline {
        var startCalls = 0
        var writeCalls = 0

        override fun start(listener: LocalSpeechTextPipeline.Listener) {
            startCalls += 1
        }

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
            writeCalls += 1
            return true
        }

        override fun finishInput() = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
