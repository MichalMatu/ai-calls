package pl.michalmatu.aicallbridge.localcall

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPlanCompletionRule
import pl.michalmatu.aicallbridge.agent.CallPlanFallbackPolicy
import pl.michalmatu.aicallbridge.agent.CallPlanProposalRule
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallRealtimeCommitmentFunctionHandler
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallTimeWindow
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFinalTurnRouteSelector
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionFollowup
import pl.michalmatu.aicallbridge.session.CallRealtimeFunctionResponder
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
class AndroidGateDBookAppointmentCompletionContractTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone)

    @Test
    fun exactConsumedCommitmentAndSuccessCompleteOwnersWithoutSpeechOrMedia() {
        val fixture = fixture()
        try {
            fixture.session.injectSyntheticFinalTranscript("mamy termin")
            val userDecision = fixture.session.applyBookAppointmentUserDecision(
                GateDBookAppointmentUserDecision.CONFIRM,
            ) as GateDBookAppointmentUserDecisionResult.Applied
            assertEquals(BookAppointmentTaskGraph.COMMITMENT, userDecision.state)

            val authorization = fixture.session.authorizeBookAppointmentCommitment()
                as GateDBookAppointmentCommitmentAuthorizationResult.Authorized
            CallRealtimeCommitmentFunctionHandler(
                workflow = fixture.workflow,
                commitmentGate = fixture.commitmentGate,
                consumptionListener = fixture.session.bookAppointmentCommitmentConsumptionListener(),
            ).onFunctionCall(
                RealtimeFunctionCall(
                    "commit-booking",
                    CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
                    "{\"authorization\":\"${authorization.authorization.value}\"}",
                ),
                NoopResponder(),
            )

            assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
            assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
            assertNull(fixture.workflow.snapshot().outcome())

            val selection = fixture.session.injectSyntheticFinalTranscript("wizyta zarezerwowana")
            assertSame(TextCallFinalTurnRoute.Consumed, selection.route)
            assertEquals(CallPlanAction.COMPLETE, selection.structuredResult?.decision()?.action())
            assertSame(fixture.outcome, selection.structuredResult?.decision()?.outcome())
            assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
            assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())

            val completion = fixture.session.completeBookAppointment(fixture.outcome)
                as GateDBookAppointmentCompletionResult.Applied
            assertEquals(BookAppointmentTaskGraph.COMPLETE, completion.state)
            assertEquals(4L, completion.generation)
            assertEquals(BookAppointmentTaskGraph.COMPLETE, fixture.acceptedResults.last().snapshot.state)
            assertEquals(CallWorkflowState.COMPLETED, fixture.workflow.snapshot().state())
            assertSame(fixture.outcome, fixture.workflow.snapshot().outcome())
            assertEquals(0, fixture.pipeline.startCalls)
            assertEquals(0, fixture.pipeline.writeCalls)
            assertEquals(0, fixture.backend.generateCalls)
        } finally {
            fixture.session.close()
        }
    }

    @Test
    fun completionBeforeConsumptionAndMismatchedSuccessFailClosedOnDevice() {
        val fixture = fixture()
        try {
            fixture.session.injectSyntheticFinalTranscript("mamy termin")
            fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
            val authorization = fixture.session.authorizeBookAppointmentCommitment()
                as GateDBookAppointmentCommitmentAuthorizationResult.Authorized

            assertEquals(
                GateDBookAppointmentCompletionResult.Rejected(
                    GateDBookAppointmentCompletionRejectReason.COMMITMENT_NOT_CONSUMED,
                ),
                fixture.session.completeBookAppointment(fixture.outcome),
            )

            CallRealtimeCommitmentFunctionHandler(
                workflow = fixture.workflow,
                commitmentGate = fixture.commitmentGate,
                consumptionListener = fixture.session.bookAppointmentCommitmentConsumptionListener(),
            ).onFunctionCall(
                RealtimeFunctionCall(
                    "commit-booking",
                    CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
                    "{\"authorization\":\"${authorization.authorization.value}\"}",
                ),
                NoopResponder(),
            )

            val wrong = CallOutcome(
                CallOutcomeStatus.SUCCESS,
                "Booked wrong slot",
                scheduledAt.plusHours(1),
                null,
                null,
                null,
                "wrong-slot",
                null,
            )
            assertEquals(
                GateDBookAppointmentCompletionResult.Rejected(
                    GateDBookAppointmentCompletionRejectReason.OUTCOME_MISMATCH,
                ),
                fixture.session.completeBookAppointment(wrong),
            )
            assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
            assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
            assertNull(fixture.workflow.snapshot().outcome())
            assertEquals(0, fixture.pipeline.startCalls)
            assertEquals(0, fixture.pipeline.writeCalls)
            assertEquals(0, fixture.backend.generateCalls)
        } finally {
            fixture.session.close()
        }
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
        val outcome = CallOutcome(
            CallOutcomeStatus.SUCCESS,
            "Booked",
            scheduledAt,
            null,
            null,
            null,
            "booking-123",
            null,
        )
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(target)
            markDialing()
            markCallActive()
        }
        val plan = CallPlan(
            task,
            target,
            emptyList(),
            listOf(CallPlanCompletionRule("booked", setOf("wizyta zarezerwowana"), outcome)),
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
        val commitComplete = TaskGraphTransitionId("commit-complete")
        val acceptedResults = mutableListOf<TaskGraphApplyResult.Accepted>()
        val commitmentGate = CallCommitmentGate { "permit-s22-completion" }
        val binding = LocalTextCallGateDProductBinding(
            deterministicInterpreter = GateDDeterministicCandidateInterpreter { turn ->
                when (turn.deterministicAction) {
                    CallPlanAction.PROPOSAL -> TaskGraphApplyCandidate.deterministic(
                        generation = turn.snapshot.generation,
                        transitionId = BookAppointmentTaskGraph.PROPOSE_APPOINTMENT_TRANSITION,
                        slotCandidates = mapOf(
                            BookAppointmentTaskGraph.APPOINTMENT_AT to
                                TaskGraphSlotValue.Text(proposal.scheduledAt().toString()),
                        ),
                    )
                    CallPlanAction.COMPLETE -> TaskGraphApplyCandidate.deterministic(
                        generation = turn.snapshot.generation,
                        transitionId = commitComplete,
                    )
                    else -> null
                }
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
                    TaskGraphApplyRule(
                        transitionId = commitComplete,
                        eventId = BookAppointmentTaskGraph.COMMIT_SUCCEEDED,
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
                if (turn.deterministicAction == CallPlanAction.PROPOSAL) {
                    assertEquals(CallPolicyAction.NEEDS_USER_DECISION, turn.deterministicPolicyDecision?.action())
                    TaskGraphApplyCandidate.deterministic(
                        generation = accepted.snapshot.generation,
                        transitionId = requireConfirmation,
                    )
                } else {
                    null
                }
            },
            bookAppointmentCommitmentGate = commitmentGate,
            callPlanCompletionMode = CallPlanCompletionMode.DEFER_TO_PRODUCT_OWNER,
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
            workflow = workflow,
            session = session,
            outcome = outcome,
            acceptedResults = acceptedResults,
            commitmentGate = commitmentGate,
            pipeline = pipeline,
            backend = backend,
        )
    }

    private data class Fixture(
        val workflow: CallWorkflow,
        val session: LocalTextCallSession,
        val outcome: CallOutcome,
        val acceptedResults: MutableList<TaskGraphApplyResult.Accepted>,
        val commitmentGate: CallCommitmentGate,
        val pipeline: RecordingPipeline,
        val backend: RecordingBackend,
    )

    private class NoopResponder : CallRealtimeFunctionResponder {
        override fun submit(outputJson: String): Result<Unit> = Result.success(Unit)
        override fun submit(outputJson: String, followup: RealtimeFunctionFollowup): Result<Unit> =
            Result.success(Unit)
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
