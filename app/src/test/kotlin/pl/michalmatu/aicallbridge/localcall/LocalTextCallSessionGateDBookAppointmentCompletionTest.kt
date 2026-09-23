package pl.michalmatu.aicallbridge.localcall

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
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

class LocalTextCallSessionGateDBookAppointmentCompletionTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone)

    @Test
    fun `consumed exact commitment plus exact success outcome completes workflow and graph exactly once`() {
        val fixture = fixture()
        val authorization = prepareCommitment(fixture)
        consumeCommitment(fixture, authorization.authorization.value)

        assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
        assertNull(fixture.workflow.snapshot().outcome())

        val selection = fixture.session.injectSyntheticFinalTranscript("wizyta zarezerwowana")
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
    }

    @Test
    fun `completion before consumption and mismatched or failure evidence fail closed`() {
        val fixture = fixture()
        prepareCommitment(fixture)

        assertEquals(
            GateDBookAppointmentCompletionResult.Rejected(
                GateDBookAppointmentCompletionRejectReason.COMMITMENT_NOT_CONSUMED,
            ),
            fixture.session.completeBookAppointment(fixture.outcome),
        )
        assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())

        val authorization = fixture.session.authorizeBookAppointmentCommitment()
        assertTrue(authorization is GateDBookAppointmentCommitmentAuthorizationResult.Rejected)
        val issued = fixture.commitmentGate.currentAuthorizationForTest()
        consumeCommitment(fixture, checkNotNull(issued))

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

        val failure = CallOutcome(
            CallOutcomeStatus.FAILURE,
            "Not booked",
            scheduledAt,
            null,
            null,
            null,
            null,
            null,
        )
        assertEquals(
            GateDBookAppointmentCompletionResult.Rejected(
                GateDBookAppointmentCompletionRejectReason.OUTCOME_NOT_SUCCESS,
            ),
            fixture.session.completeBookAppointment(failure),
        )
        assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
        assertNull(fixture.workflow.snapshot().outcome())
    }

    @Test
    fun `generic finalized turn candidate cannot own commit-complete transition`() {
        val fixture = fixture()
        val authorization = prepareCommitment(fixture)
        consumeCommitment(fixture, authorization.authorization.value)

        fixture.session.injectSyntheticFinalTranscript("wizyta zarezerwowana")

        assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
        assertNull(fixture.workflow.snapshot().outcome())
    }

    private fun prepareCommitment(
        fixture: Fixture,
    ): GateDBookAppointmentCommitmentAuthorizationResult.Authorized {
        fixture.session.injectSyntheticFinalTranscript("mamy termin")
        assertTrue(
            fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
                is GateDBookAppointmentUserDecisionResult.Applied,
        )
        return fixture.session.authorizeBookAppointmentCommitment()
            as GateDBookAppointmentCommitmentAuthorizationResult.Authorized
    }

    private fun consumeCommitment(fixture: Fixture, authorization: String) {
        val handler = CallRealtimeCommitmentFunctionHandler(
            workflow = fixture.workflow,
            commitmentGate = fixture.commitmentGate,
            consumptionListener = fixture.session.bookAppointmentCommitmentConsumptionListener(),
        )
        handler.onFunctionCall(
            RealtimeFunctionCall(
                "commit-booking",
                CallRealtimeCommitmentFunctionHandler.FUNCTION_NAME,
                "{\"authorization\":\"$authorization\"}",
            ),
            NoopResponder(),
        )
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
        val commitmentGate = TestableCommitmentGate("permit-final-completion")
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
            bookAppointmentCommitmentGate = commitmentGate.delegate,
            callPlanCompletionMode = CallPlanCompletionMode.DEFER_TO_PRODUCT_OWNER,
        )
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
        return Fixture(
            workflow = workflow,
            session = session,
            outcome = outcome,
            acceptedResults = acceptedResults,
            commitmentGate = commitmentGate,
        )
    }

    private data class Fixture(
        val workflow: CallWorkflow,
        val session: LocalTextCallSession,
        val outcome: CallOutcome,
        val acceptedResults: MutableList<TaskGraphApplyResult.Accepted>,
        val commitmentGate: TestableCommitmentGate,
    )

    private class TestableCommitmentGate(token: String) {
        val delegate = CallCommitmentGate { token }
        fun currentAuthorizationForTest(): String? = if (delegate.hasAuthorization()) "permit-final-completion" else null
        fun hasAuthorization(): Boolean = delegate.hasAuthorization()
        fun consume(value: String) = delegate.consume(value)
    }

    private class NoopResponder : CallRealtimeFunctionResponder {
        override fun submit(outputJson: String): Result<Unit> = Result.success(Unit)
        override fun submit(outputJson: String, followup: RealtimeFunctionFollowup): Result<Unit> =
            Result.success(Unit)
    }

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
