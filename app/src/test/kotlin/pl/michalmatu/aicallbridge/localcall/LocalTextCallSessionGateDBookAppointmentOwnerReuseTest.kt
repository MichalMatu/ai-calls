package pl.michalmatu.aicallbridge.localcall

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanProposalRule
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyCandidate
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyPolicy
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyRule
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplySlotRule
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplySlotSchema
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphCandidateProvenance
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalTextCallSessionGateDBookAppointmentOwnerReuseTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone)

    @Test
    fun `synthetic proposal reuses CallPlan workflow decision exactly once before Gate D follow-up`() {
        val allowed = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 18, 0, 0, 0, zone),
        )
        val preferred = CallTimeWindow(
            ZonedDateTime.of(2026, 9, 24, 16, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 9, 24, 17, 0, 0, 0, zone),
        )
        val task = CallTask(
            "Clinic A",
            "book appointment",
            "appointment",
            CallConstraints(listOf(allowed), null, emptySet()),
            CallPreferences(listOf(preferred), emptyList(), emptyList()),
            emptyMap(),
        )
        val target = CallResolvedTarget("Clinic A", "+48123456789")
        val proposal = CallProposal(scheduledAt, null, null, null, null)
        val workflowStates = mutableListOf<CallWorkflowState>()
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { snapshot ->
            workflowStates += snapshot.state()
        }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            emptyList(),
            listOf(CallPlanProposalRule("offer", setOf("mamy termin"), proposal)),
            CallPlanFallbackPolicy.repeatThenTakeOver(1),
        )
        val facts = AuthorizedFactSnapshot(
            task = task,
            target = target,
            generation = 0L,
            availableFields = emptySet(),
            authorizedFields = emptySet(),
        )
        val applyResults = mutableListOf<TaskGraphApplyResult>()
        val requireConfirmationTransition = TaskGraphTransitionId("require-confirmation")
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { turn ->
                assertEquals(CallPolicyAction.PROPOSAL, turn.deterministicAction)
                assertEquals(proposal, turn.deterministicProposal)
                assertEquals(
                    CallPolicyAction.NEEDS_USER_DECISION,
                    turn.deterministicPolicyDecision?.action(),
                )
                TaskGraphApplyCandidate.deterministic(
                    generation = turn.snapshot.generation,
                    transitionId = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT_TRANSITION,
                    slotCandidates = mapOf(
                        BookAppointmentTaskGraph.APPOINTMENT_AT to
                            TaskGraphSlotValue.Text(proposal.scheduledAt().toString()),
                    ),
                )
            },
            applyPolicy = TaskGraphApplyPolicy(
                listOf(
                    TaskGraphApplyRule(
                        transitionId = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT_TRANSITION,
                        eventId = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT,
                        allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC),
                        slotRules = mapOf(
                            BookAppointmentTaskGraph.APPOINTMENT_AT to TaskGraphApplySlotRule(
                                required = true,
                                schema = TaskGraphApplySlotSchema.Text(),
                            ),
                        ),
                    ),
                    TaskGraphApplyRule(
                        transitionId = requireConfirmationTransition,
                        eventId = BookAppointmentTaskGraph.REQUIRE_CONFIRMATION,
                        allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC),
                    ),
                ),
            ),
            authorizedSlotIdsProvider = GateDAuthorizedSlotIdsProvider { snapshot ->
                if (snapshot.state == BookAppointmentTaskGraph.WAITING_OFFER) {
                    setOf(BookAppointmentTaskGraph.APPOINTMENT_AT)
                } else {
                    emptySet()
                }
            },
            applyResultListener = GateDTaskGraphApplyResultListener { applyResults += it },
            deterministicFollowUpRouter = GateDDeterministicFollowUpRouter { turn, accepted ->
                assertEquals(BookAppointmentTaskGraph.PROPOSAL, accepted.snapshot.state)
                assertEquals(proposal, turn.deterministicProposal)
                assertEquals(
                    CallPolicyAction.NEEDS_USER_DECISION,
                    turn.deterministicPolicyDecision?.action(),
                )
                TaskGraphApplyCandidate.deterministic(
                    generation = accepted.snapshot.generation,
                    transitionId = requireConfirmationTransition,
                )
            },
        )
        val pipeline = FakePipeline()
        val backend = FakeBackend()
        val session = LocalTextCallSession(
            prepared = PreparedLocalTextCall(
                workflow = workflow,
                backend = backend,
                callPlan = plan,
                taskGraph = BookAppointmentTaskGraph.definition,
                authorizedFacts = facts,
            ),
            pipelineFactory = LocalTextCallSession.PipelineFactory { pipeline },
            gateDProductBinding = binding,
        )

        val selection = session.injectSyntheticFinalTranscript("mamy termin")

        assertEquals(TextCallFinalTurnRoute.Consumed, selection.route)
        assertEquals(CallPolicyAction.PROPOSAL, selection.structuredResult?.decision()?.action())
        assertEquals(CallPolicyAction.NEEDS_USER_DECISION, selection.structuredResult?.policyDecision()?.action())
        assertEquals(CallWorkflowState.NEEDS_USER_DECISION, workflow.snapshot().state())
        assertEquals(proposal, workflow.snapshot().pendingProposal())
        assertEquals(1, workflowStates.count { it == CallWorkflowState.NEEDS_USER_DECISION })
        assertEquals(2, applyResults.size)
        assertEquals(
            BookAppointmentTaskGraph.PROPOSAL,
            (applyResults[0] as TaskGraphApplyResult.Accepted).snapshot.state,
        )
        assertEquals(
            BookAppointmentTaskGraph.CONFIRMATION,
            (applyResults[1] as TaskGraphApplyResult.Accepted).snapshot.state,
        )
        assertEquals(0, pipeline.startCalls)
        assertEquals(0, pipeline.writeCalls)
        assertEquals(0, backend.generateCalls)
        assertFalse(pipeline.cancelled)
        assertTrue(workflow.snapshot().outcome() == null)
        session.close()
    }

    private class FakeBackend : TextCallAgentBackend {
        var generateCalls = 0
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generateCalls += 1
        }
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
        var startCalls = 0
        var writeCalls = 0
        var cancelled = false

        override fun start(listener: LocalSpeechTextPipeline.Listener) {
            startCalls += 1
        }

        override fun writeInputPcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
            writeCalls += 1
            return true
        }

        override fun finishInput() = Unit
        override fun cancel() {
            cancelled = true
        }
        override fun close() = Unit
    }
}
