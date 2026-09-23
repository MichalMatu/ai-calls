package pl.michalmatu.aicallbridge.localcall

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

class LocalTextCallSessionGateDBookAppointmentCommitmentAuthorizationTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val scheduledAt = ZonedDateTime.of(2026, 9, 24, 17, 30, 0, 0, zone)

    @Test
    fun `confirmed exact proposal issues one opaque permit without completing graph or workflow`() {
        val fixture = fixture()

        fixture.session.injectSyntheticFinalTranscript("mamy termin")
        assertTrue(
            fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
                is GateDBookAppointmentUserDecisionResult.Applied,
        )

        val authorization = fixture.session.authorizeBookAppointmentCommitment()
            as GateDBookAppointmentCommitmentAuthorizationResult.Authorized

        assertEquals("permit-appointment-1", authorization.authorization.value)
        assertFalse(authorization.toString().contains("permit-appointment-1"))
        assertTrue(fixture.commitmentGate.hasAuthorization())
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())
        assertNull(fixture.workflow.snapshot().pendingProposal())
        assertNull(fixture.workflow.snapshot().outcome())
        assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
        assertEquals(3L, fixture.acceptedResults.last().snapshot.generation)

        val consumed = fixture.commitmentGate.consume(authorization.authorization.value).getOrThrow()
        assertEquals(fixture.proposal, consumed)
        assertFalse(fixture.commitmentGate.hasAuthorization())
        assertEquals(BookAppointmentTaskGraph.COMMITMENT, fixture.acceptedResults.last().snapshot.state)
        assertNull(fixture.workflow.snapshot().outcome())

        assertEquals(
            GateDBookAppointmentCommitmentAuthorizationResult.Rejected(
                GateDBookAppointmentCommitmentAuthorizationRejectReason.ALREADY_ISSUED,
            ),
            fixture.session.authorizeBookAppointmentCommitment(),
        )
        fixture.session.close()
    }

    @Test
    fun `rejected proposal never issues commitment permit`() {
        val fixture = fixture()

        fixture.session.injectSyntheticFinalTranscript("mamy termin")
        fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.REJECT)

        assertFalse(fixture.commitmentGate.hasAuthorization())
        assertEquals(
            GateDBookAppointmentCommitmentAuthorizationResult.Rejected(
                GateDBookAppointmentCommitmentAuthorizationRejectReason.GRAPH_NOT_COMMITMENT,
            ),
            fixture.session.authorizeBookAppointmentCommitment(),
        )
        assertFalse(fixture.commitmentGate.hasAuthorization())
        fixture.session.close()
    }

    @Test
    fun `stale workflow fails closed before commitment permit issuance`() {
        val fixture = fixture()
        fixture.session.injectSyntheticFinalTranscript("mamy termin")
        assertTrue(
            fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
                is GateDBookAppointmentUserDecisionResult.Applied,
        )
        assertEquals(CallWorkflowState.ACTIVE_NEGOTIATION, fixture.workflow.snapshot().state())

        fixture.workflow.fail(IllegalStateException("stale-workflow"))

        val result = fixture.session.authorizeBookAppointmentCommitment()
        assertTrue(result is GateDBookAppointmentCommitmentAuthorizationResult.Rejected)
        assertFalse(fixture.commitmentGate.hasAuthorization())
        fixture.session.close()
    }

    @Test
    fun `foreign commitment permits survive reject and cancel boundaries`() {
        val rejected = fixture()
        rejected.session.injectSyntheticFinalTranscript("mamy termin")
        val rejectForeign = rejected.commitmentGate.authorize(rejected.proposal)

        rejected.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.REJECT)

        assertEquals(
            rejected.proposal,
            rejected.commitmentGate.consume(rejectForeign.value).getOrThrow(),
        )
        rejected.session.close()

        val cancelled = fixture()
        val cancelForeign = cancelled.commitmentGate.authorize(cancelled.proposal)

        cancelled.session.cancel()

        assertEquals(
            cancelled.proposal,
            cancelled.commitmentGate.consume(cancelForeign.value).getOrThrow(),
        )
        cancelled.session.close()
    }

    @Test
    fun `cancel revokes an unconsumed appointment commitment permit`() {
        val fixture = fixture()
        fixture.session.injectSyntheticFinalTranscript("mamy termin")
        fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
        assertTrue(
            fixture.session.authorizeBookAppointmentCommitment()
                is GateDBookAppointmentCommitmentAuthorizationResult.Authorized,
        )
        assertTrue(fixture.commitmentGate.hasAuthorization())

        fixture.session.cancel()

        assertFalse(fixture.commitmentGate.hasAuthorization())
        assertEquals(
            GateDBookAppointmentCommitmentAuthorizationResult.Rejected(
                GateDBookAppointmentCommitmentAuthorizationRejectReason.INTEGRATION_INACTIVE,
            ),
            fixture.session.authorizeBookAppointmentCommitment(),
        )
        fixture.session.close()
    }

    @Test
    fun `close revokes an unconsumed appointment commitment permit`() {
        val fixture = fixture()
        fixture.session.injectSyntheticFinalTranscript("mamy termin")
        fixture.session.applyBookAppointmentUserDecision(GateDBookAppointmentUserDecision.CONFIRM)
        fixture.session.authorizeBookAppointmentCommitment()
        assertTrue(fixture.commitmentGate.hasAuthorization())

        fixture.session.close()

        assertFalse(fixture.commitmentGate.hasAuthorization())
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
        val commitmentGate = CallCommitmentGate { "permit-appointment-1" }
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
        return Fixture(
            proposal = proposal,
            workflow = workflow,
            session = session,
            acceptedResults = acceptedResults,
            commitmentGate = commitmentGate,
        )
    }

    private data class Fixture(
        val proposal: CallProposal,
        val workflow: CallWorkflow,
        val session: LocalTextCallSession,
        val acceptedResults: MutableList<TaskGraphApplyResult.Accepted>,
        val commitmentGate: CallCommitmentGate,
    )

    private class FakeBackend : TextCallAgentBackend {
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FakePipeline : LocalTextCallSession.Pipeline {
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
