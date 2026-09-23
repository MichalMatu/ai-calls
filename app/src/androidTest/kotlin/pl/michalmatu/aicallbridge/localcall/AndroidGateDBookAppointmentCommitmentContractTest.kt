package pl.michalmatu.aicallbridge.localcall

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
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
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
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

@RunWith(AndroidJUnit4::class)
class AndroidGateDBookAppointmentCommitmentContractTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone)

    @Test
    fun syntheticProposalUserConfirmationAndPermitStayNoCallAndStopBeforeCompletion() {
        val fixture = fixture()

        try {
            val selection = fixture.session.injectSyntheticFinalTranscript("mamy termin")

            assertEquals(TextCallFinalTurnRoute.Consumed, selection.route)
            assertEquals(CallWorkflowState.NEEDS_USER_DECISION, fixture.workflow.snapshot().state())
            assertEquals(fixture.proposal, fixture.workflow.snapshot().pendingProposal())
            assertEquals(BookAppointmentTaskGraph.CONFIRMATION, fixture.acceptedResults.last().snapshot.state)
            assertEquals(2L, fixture.acceptedResults.last().snapshot.generation)
            assertEquals(0, fixture.pipeline.startCalls)
            assertEquals(0, fixture.pipeline.writeCalls)
            assertEquals(0, fixture.backend.generateCalls)

            val userDecision = fixture.session.applyBookAppointmentUserDecision(
                GateDBookAppointmentUserDecision.CONFIRM,
            ) as GateDBookAppointmentUserDecisionResult.Applied

            assertEquals(BookAppointmentTaskGraph.COMMITMENT, userDecision.state)
            assertEquals(3L, userDecision.generation)
            assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
            assertNull(fixture.workflow.snapshot().pendingProposal())
            assertNull(fixture.workflow.snapshot().outcome())

            val authorization = fixture.session.authorizeBookAppointmentCommitment()
                as GateDBookAppointmentCommitmentAuthorizationResult.Authorized

            assertEquals("permit-s22-book-appointment", authorization.authorization.value)
            assertFalse(authorization.toString().contains("permit-s22-book-appointment"))
            assertTrue(fixture.commitmentGate.hasAuthorization())
            assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
            assertEquals(3L, fixture.acceptedResults.last().snapshot.generation)
            assertNull(fixture.workflow.snapshot().outcome())
            assertEquals(0, fixture.pipeline.startCalls)
            assertEquals(0, fixture.pipeline.writeCalls)
            assertEquals(0, fixture.backend.generateCalls)
        } finally {
            fixture.session.close()
        }

        assertFalse(fixture.commitmentGate.hasAuthorization())
    }

    @Test
    fun staleWorkflowAndForeignPermitFailClosedOnDevice() {
        val stale = fixture()
        try {
            stale.session.injectSyntheticFinalTranscript("mamy termin")
            stale.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
            stale.workflow.fail(IllegalStateException("stale-device-workflow"))

            val result = stale.session.authorizeBookAppointmentCommitment()
            assertTrue(result is GateDBookAppointmentCommitmentAuthorizationResult.Rejected)
            assertFalse(stale.commitmentGate.hasAuthorization())
        } finally {
            stale.session.close()
        }

        val foreign = fixture()
        val foreignAuthorization = foreign.commitmentGate.authorize(foreign.proposal)
        foreign.session.cancel()

        assertEquals(
            foreign.proposal,
            foreign.commitmentGate.consume(foreignAuthorization.value).getOrThrow(),
        )
        foreign.session.close()
    }

    private fun fixture(): Fixture {
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
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
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
        val requireConfirmation = TaskGraphTransitionId("require-confirmation")
        val confirmProposal = TaskGraphTransitionId("confirm-proposal")
        val rejectProposal = TaskGraphTransitionId("reject-proposal")
        val acceptedResults = mutableListOf<TaskGraphApplyResult.Accepted>()
        val commitmentGate = CallCommitmentGate { "permit-s22-book-appointment" }
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { turn ->
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
                        transitionId = requireConfirmation,
                        eventId = BookAppointmentTaskGraph.REQUIRE_CONFIRMATION,
                        allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC),
                    ),
                    TaskGraphApplyRule(
                        transitionId = confirmProposal,
                        eventId = BookAppointmentTaskGraph.USER_CONFIRMED,
                        allowedProvenance = setOf(TaskGraphCandidateProvenance.DETERMINISTIC),
                    ),
                    TaskGraphApplyRule(
                        transitionId = rejectProposal,
                        eventId = BookAppointmentTaskGraph.USER_REJECTED,
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
            applyResultListener = GateDTaskGraphApplyResultListener { result ->
                if (result is TaskGraphApplyResult.Accepted) acceptedResults += result
            },
            deterministicFollowUpRouter = GateDDeterministicFollowUpRouter { turn, accepted ->
                assertEquals(CallPolicyAction.NEEDS_USER_DECISION, turn.deterministicPolicyDecision?.action())
                TaskGraphApplyCandidate.deterministic(
                    generation = accepted.snapshot.generation,
                    transitionId = requireConfirmation,
                )
            },
            bookAppointmentCommitmentGate = commitmentGate,
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
            gateDProductBinding = binding,
        )
        return Fixture(
            proposal = proposal,
            workflow = workflow,
            session = session,
            acceptedResults = acceptedResults,
            commitmentGate = commitmentGate,
            pipeline = pipeline,
            backend = backend,
        )
    }

    private data class Fixture(
        val proposal: CallProposal,
        val workflow: CallWorkflow,
        val session: LocalTextCallSession,
        val acceptedResults: MutableList<TaskGraphApplyResult.Accepted>,
        val commitmentGate: CallCommitmentGate,
        val pipeline: RecordingPipeline,
        val backend: RecordingBackend,
    )

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

        override fun start(
            listener: LocalSpeechTextPipeline.Listener,
            finalTurnRouteSelector: LocalSpeechFinalTurnRouteSelector,
        ) {
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
