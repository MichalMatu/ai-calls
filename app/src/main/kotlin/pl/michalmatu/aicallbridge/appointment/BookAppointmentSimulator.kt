package pl.michalmatu.aicallbridge.appointment

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.function.Consumer
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallOutcome
import pl.michalmatu.aicallbridge.agent.CallOutcomeStatus
import pl.michalmatu.aicallbridge.agent.CallPolicyAction
import pl.michalmatu.aicallbridge.agent.CallPolicyReason
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowSnapshot
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.DefaultFactDisclosurePolicy
import pl.michalmatu.aicallbridge.identity.FactDisclosureDecision
import pl.michalmatu.aicallbridge.identity.FactDisclosureRequest
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.taskgraph.CustomTaskGraphCore
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEffect
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEffectId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEvent
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphEventRecord
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphRecoveryMutation
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphReduction
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphReplayResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateKind
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

object BookAppointmentTaskGraph {
    val WAITING_OFFER = TaskGraphStateId("WAITING_OFFER")
    val PROPOSAL = TaskGraphStateId("PROPOSAL")
    val CONFIRMATION = TaskGraphStateId("CONFIRMATION")
    val COMMITMENT = TaskGraphStateId("COMMITMENT")
    val COMPLETE = TaskGraphStateId("COMPLETE")
    val CANCELLED = TaskGraphStateId("CANCELLED")
    val TAKE_OVER = TaskGraphStateId("TAKE_OVER")

    val APPOINTMENT_AT = TaskGraphSlotId("APPOINTMENT_AT")

    internal val ACCEPT_AUTONOMOUS = TaskGraphEventId("ACCEPT_AUTONOMOUS")
    internal val PROCEED_TO_COMMITMENT = TaskGraphEventId("PROCEED_TO_COMMITMENT")
    internal val REQUIRE_CONFIRMATION = TaskGraphEventId("REQUIRE_CONFIRMATION")
    internal val USER_CONFIRMED = TaskGraphEventId("USER_CONFIRMED")
    internal val USER_REJECTED = TaskGraphEventId("USER_REJECTED")
    internal val COMMIT_SUCCEEDED = TaskGraphEventId("COMMIT_SUCCEEDED")
    internal val NO_AVAILABILITY = TaskGraphEventId("NO_AVAILABILITY")
    internal val CLARIFICATION = TaskGraphEventId("CLARIFICATION")
    internal val HARMLESS_QUESTION = TaskGraphEventId("HARMLESS_QUESTION")
    internal val CANCEL = TaskGraphEventId("CANCEL")
    internal val TAKE_OVER_EVENT = TaskGraphEventId("TAKE_OVER")

    internal val ACCEPT_AUTONOMOUS_TRANSITION = TaskGraphTransitionId("accept-autonomous")
    internal val REQUIRE_CONFIRMATION_TRANSITION = TaskGraphTransitionId("require-confirmation")
    internal val EVALUATE_PROPOSAL_EFFECT =
        TaskGraphEffect(TaskGraphEffectId("BOOK_APPOINTMENT_EVALUATE_PROPOSAL"))

    val definition = TaskGraphDefinition(
        version = 1,
        initialState = WAITING_OFFER,
        states = listOf(
            TaskGraphStateDefinition(WAITING_OFFER),
            TaskGraphStateDefinition(PROPOSAL, TaskGraphStateKind.PROPOSAL),
            TaskGraphStateDefinition(CONFIRMATION, TaskGraphStateKind.CONFIRMATION),
            TaskGraphStateDefinition(COMMITMENT, TaskGraphStateKind.COMMITMENT),
            TaskGraphStateDefinition(COMPLETE, TaskGraphStateKind.COMPLETED),
            TaskGraphStateDefinition(CANCELLED, TaskGraphStateKind.COMPLETED),
            TaskGraphStateDefinition(TAKE_OVER, TaskGraphStateKind.TAKE_OVER),
        ),
        transitions = listOf(
            TaskGraphTransition(
                id = ACCEPT_AUTONOMOUS_TRANSITION,
                from = WAITING_OFFER,
                event = ACCEPT_AUTONOMOUS,
                to = PROPOSAL,
                contextReducer = ::commitAppointmentCandidate,
                effects = listOf(EVALUATE_PROPOSAL_EFFECT),
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("proposal-to-commitment"),
                from = PROPOSAL,
                event = PROCEED_TO_COMMITMENT,
                to = COMMITMENT,
            ),
            TaskGraphTransition(
                id = REQUIRE_CONFIRMATION_TRANSITION,
                from = WAITING_OFFER,
                event = REQUIRE_CONFIRMATION,
                to = CONFIRMATION,
                contextReducer = ::commitAppointmentCandidate,
                effects = listOf(EVALUATE_PROPOSAL_EFFECT),
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("confirm-proposal"),
                from = CONFIRMATION,
                event = USER_CONFIRMED,
                to = COMMITMENT,
                contextReducer = ::commitAppointmentCandidate,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("reject-proposal"),
                from = CONFIRMATION,
                event = USER_REJECTED,
                to = WAITING_OFFER,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("commit-complete"),
                from = COMMITMENT,
                event = COMMIT_SUCCEEDED,
                to = COMPLETE,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("no-availability"),
                from = WAITING_OFFER,
                event = NO_AVAILABILITY,
                to = COMPLETE,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("clarification-recovery"),
                from = WAITING_OFFER,
                event = CLARIFICATION,
                to = WAITING_OFFER,
                recovery = TaskGraphRecoveryMutation.INCREMENT,
            ),
            TaskGraphTransition(
                id = TaskGraphTransitionId("harmless-question-recovery"),
                from = WAITING_OFFER,
                event = HARMLESS_QUESTION,
                to = WAITING_OFFER,
                recovery = TaskGraphRecoveryMutation.INCREMENT,
            ),
            terminalTransition("cancel-waiting", WAITING_OFFER, CANCEL, CANCELLED),
            terminalTransition("cancel-confirmation", CONFIRMATION, CANCEL, CANCELLED),
            terminalTransition("takeover-waiting", WAITING_OFFER, TAKE_OVER_EVENT, TAKE_OVER),
            terminalTransition("takeover-confirmation", CONFIRMATION, TAKE_OVER_EVENT, TAKE_OVER),
        ),
        maxRecoveryCount = 2,
    )

    private fun commitAppointmentCandidate(
        context: pl.michalmatu.aicallbridge.taskgraph.TaskGraphContext,
        event: TaskGraphEvent,
    ): pl.michalmatu.aicallbridge.taskgraph.TaskGraphContext {
        val value = requireNotNull(event.candidates[APPOINTMENT_AT]) {
            "accepted appointment transition requires APPOINTMENT_AT"
        }
        return context.with(APPOINTMENT_AT, value)
    }

    private fun terminalTransition(
        id: String,
        from: TaskGraphStateId,
        event: TaskGraphEventId,
        to: TaskGraphStateId,
    ) = TaskGraphTransition(
        id = TaskGraphTransitionId(id),
        from = from,
        event = event,
        to = to,
    )
}

sealed interface BookAppointmentSimulationStep {
    data class ReceptionistOffer(val text: String) : BookAppointmentSimulationStep
    data class IdentityRequest(val fieldId: IdentityFieldId) : BookAppointmentSimulationStep
    data object NoAvailability : BookAppointmentSimulationStep
    data object UserRejectsProposal : BookAppointmentSimulationStep
    data object UserConfirmsProposal : BookAppointmentSimulationStep
    data object UnexpectedHarmlessQuestion : BookAppointmentSimulationStep
    data object Cancel : BookAppointmentSimulationStep
    data object TakeOver : BookAppointmentSimulationStep
}

enum class BookAppointmentOutcome {
    BOOKED,
    NO_AVAILABILITY,
    CANCELLED,
    TAKE_OVER,
    INCOMPLETE,
}

enum class BookAppointmentEvidenceType {
    OFFER_PARSED,
    OFFER_REJECTED,
    CLARIFICATION_REQUIRED,
    PROPOSAL_CREATED,
    USER_CONFIRMATION_REQUIRED,
    USER_REJECTED_PROPOSAL,
    USER_CONFIRMED_PROPOSAL,
    COMMITMENT_CONSUMED,
    DISCLOSURE_ALLOWED,
    DISCLOSURE_ASK_USER,
    DISCLOSURE_DENIED,
    HARMLESS_QUESTION,
}

data class BookAppointmentEvidence(
    val type: BookAppointmentEvidenceType,
    val detail: String? = null,
)

data class BookAppointmentSimulationResult(
    val outcome: BookAppointmentOutcome,
    val finalSnapshot: TaskGraphSnapshot,
    val replayVerified: Boolean,
    val evidence: List<BookAppointmentEvidence>,
    val eventRecords: List<TaskGraphEventRecord>,
)

/** Host-only deterministic receptionist simulator. It executes no telephony or Android side effects. */
class BookAppointmentSimulator(
    private val task: CallTask,
    private val target: CallResolvedTarget,
    private val authorizedFacts: AuthorizedFactSnapshot,
    private val zone: ZoneId,
    private val referenceDate: LocalDate? = null,
) {
    private val appointmentInterpreter = AppointmentInterpreter(zone)

    fun run(steps: List<BookAppointmentSimulationStep>): BookAppointmentSimulationResult {
        val core = CustomTaskGraphCore(BookAppointmentTaskGraph.definition)
        val initialSnapshot = BookAppointmentTaskGraph.definition.initialSnapshot()
        var snapshot = initialSnapshot
        val records = mutableListOf<TaskGraphEventRecord>()
        val evidence = mutableListOf<BookAppointmentEvidence>()
        val confirmationPolicy = CallConfirmationPolicy()
        val workflow = CallWorkflow(
            task,
            confirmationPolicy,
            Consumer<CallWorkflowSnapshot> { },
        ).also {
            it.resolveTarget(target)
            it.markDialing()
            it.markCallActive()
        }
        val commitmentGate = CallCommitmentGate { "simulator-token" }
        val disclosurePolicy = DefaultFactDisclosurePolicy()
        var pendingProposal: CallProposal? = null
        var outcome = BookAppointmentOutcome.INCOMPLETE

        fun dispatch(
            eventId: TaskGraphEventId,
            candidates: Map<TaskGraphSlotId, TaskGraphSlotValue> = emptyMap(),
        ) {
            val reduction = core.reduce(
                snapshot,
                TaskGraphEvent(eventId, snapshot.generation, candidates),
            )
            when (reduction) {
                is TaskGraphReduction.Accepted -> {
                    snapshot = reduction.snapshot
                    records += reduction.record
                }
                is TaskGraphReduction.Rejected -> error(
                    "BOOK_APPOINTMENT graph rejected $eventId from ${snapshot.state}: ${reduction.reason}",
                )
            }
        }

        fun finishBooking(proposal: CallProposal, alreadyInCommitmentState: Boolean) {
            val authorization = commitmentGate.authorize(proposal)
            if (!alreadyInCommitmentState) {
                dispatch(BookAppointmentTaskGraph.PROCEED_TO_COMMITMENT)
            }
            val consumed = commitmentGate.consume(authorization.value).getOrThrow()
            check(consumed == proposal) { "commitment permit changed concrete proposal" }
            evidence += BookAppointmentEvidence(BookAppointmentEvidenceType.COMMITMENT_CONSUMED)
            dispatch(BookAppointmentTaskGraph.COMMIT_SUCCEEDED)
            workflow.complete(
                CallOutcome(
                    CallOutcomeStatus.SUCCESS,
                    "appointment booked",
                    proposal.scheduledAt(),
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
            )
            outcome = BookAppointmentOutcome.BOOKED
        }

        stepLoop@ for (step in steps) {
            when (step) {
                is BookAppointmentSimulationStep.ReceptionistOffer -> {
                    if (snapshot.state != BookAppointmentTaskGraph.WAITING_OFFER) {
                        continue@stepLoop
                    }
                    val scheduledAt = parseOffer(step.text)
                    if (scheduledAt == null) {
                        evidence += BookAppointmentEvidence(BookAppointmentEvidenceType.CLARIFICATION_REQUIRED)
                        dispatch(BookAppointmentTaskGraph.CLARIFICATION)
                        continue@stepLoop
                    }
                    evidence += BookAppointmentEvidence(
                        BookAppointmentEvidenceType.OFFER_PARSED,
                        scheduledAt.toString(),
                    )
                    val proposal = CallProposal(scheduledAt, null, null, null, null)
                    val preDecision = confirmationPolicy.evaluate(task, proposal)
                    if (preDecision.reasons().any { it in HARD_POLICY_REASONS }) {
                        evidence += BookAppointmentEvidence(
                            BookAppointmentEvidenceType.OFFER_REJECTED,
                            preDecision.reasons().joinToString(",") { it.name },
                        )
                        continue@stepLoop
                    }

                    val workflowDecision = workflow.evaluateProposal(proposal)
                    check(workflowDecision == preDecision) { "workflow policy decision diverged" }
                    evidence += BookAppointmentEvidence(BookAppointmentEvidenceType.PROPOSAL_CREATED)

                    if (workflowDecision.action() == CallPolicyAction.NEEDS_USER_DECISION) {
                        pendingProposal = proposal
                        dispatch(
                            BookAppointmentTaskGraph.REQUIRE_CONFIRMATION,
                            mapOf(
                                BookAppointmentTaskGraph.APPOINTMENT_AT to
                                    TaskGraphSlotValue.Text(scheduledAt.toString()),
                            ),
                        )
                        evidence += BookAppointmentEvidence(
                            BookAppointmentEvidenceType.USER_CONFIRMATION_REQUIRED,
                            workflowDecision.reasons().joinToString(",") { it.name },
                        )
                    } else {
                        dispatch(
                            BookAppointmentTaskGraph.ACCEPT_AUTONOMOUS,
                            mapOf(
                                BookAppointmentTaskGraph.APPOINTMENT_AT to
                                    TaskGraphSlotValue.Text(scheduledAt.toString()),
                            ),
                        )
                        finishBooking(proposal, alreadyInCommitmentState = false)
                        break@stepLoop
                    }
                }

                BookAppointmentSimulationStep.UserRejectsProposal -> {
                    if (pendingProposal != null && snapshot.state == BookAppointmentTaskGraph.CONFIRMATION) {
                        workflow.rejectPendingProposal()
                        pendingProposal = null
                        dispatch(BookAppointmentTaskGraph.USER_REJECTED)
                        evidence += BookAppointmentEvidence(BookAppointmentEvidenceType.USER_REJECTED_PROPOSAL)
                    }
                }

                BookAppointmentSimulationStep.UserConfirmsProposal -> {
                    val proposal = pendingProposal
                    if (proposal != null && snapshot.state == BookAppointmentTaskGraph.CONFIRMATION) {
                        val approved = workflow.approvePendingProposal()
                        check(approved == proposal) { "workflow approval changed pending proposal" }
                        pendingProposal = null
                        evidence += BookAppointmentEvidence(BookAppointmentEvidenceType.USER_CONFIRMED_PROPOSAL)
                        dispatch(
                            BookAppointmentTaskGraph.USER_CONFIRMED,
                            mapOf(
                                BookAppointmentTaskGraph.APPOINTMENT_AT to
                                    TaskGraphSlotValue.Text(proposal.scheduledAt().toString()),
                            ),
                        )
                        finishBooking(proposal, alreadyInCommitmentState = true)
                        break@stepLoop
                    }
                }

                is BookAppointmentSimulationStep.IdentityRequest -> {
                    val decision = disclosurePolicy.decide(
                        FactDisclosureRequest(
                            task = task,
                            target = target,
                            currentState = snapshot.state,
                            fieldId = step.fieldId,
                            mode = CallTaskMode.GENUINE,
                            snapshotGeneration = authorizedFacts.generation,
                        ),
                        authorizedFacts,
                    )
                    evidence += BookAppointmentEvidence(
                        type = when (decision) {
                            FactDisclosureDecision.ALLOW -> BookAppointmentEvidenceType.DISCLOSURE_ALLOWED
                            FactDisclosureDecision.ASK_USER -> BookAppointmentEvidenceType.DISCLOSURE_ASK_USER
                            FactDisclosureDecision.DENY -> BookAppointmentEvidenceType.DISCLOSURE_DENIED
                        },
                        detail = step.fieldId.name,
                    )
                }

                BookAppointmentSimulationStep.NoAvailability -> {
                    if (snapshot.state == BookAppointmentTaskGraph.WAITING_OFFER) {
                        dispatch(BookAppointmentTaskGraph.NO_AVAILABILITY)
                        workflow.complete(
                            CallOutcome(
                                CallOutcomeStatus.FAILURE,
                                "no suitable appointment available",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                            ),
                        )
                        outcome = BookAppointmentOutcome.NO_AVAILABILITY
                        break@stepLoop
                    }
                }

                BookAppointmentSimulationStep.UnexpectedHarmlessQuestion -> {
                    if (snapshot.state == BookAppointmentTaskGraph.WAITING_OFFER) {
                        evidence += BookAppointmentEvidence(BookAppointmentEvidenceType.HARMLESS_QUESTION)
                        dispatch(BookAppointmentTaskGraph.HARMLESS_QUESTION)
                    }
                }

                BookAppointmentSimulationStep.Cancel -> {
                    commitmentGate.clear()
                    dispatch(BookAppointmentTaskGraph.CANCEL)
                    outcome = BookAppointmentOutcome.CANCELLED
                    break@stepLoop
                }

                BookAppointmentSimulationStep.TakeOver -> {
                    commitmentGate.clear()
                    dispatch(BookAppointmentTaskGraph.TAKE_OVER_EVENT)
                    outcome = BookAppointmentOutcome.TAKE_OVER
                    break@stepLoop
                }
            }
        }

        val replay = core.replay(initialSnapshot, records)
        val replayVerified = replay is TaskGraphReplayResult.Success && replay.snapshot == snapshot
        return BookAppointmentSimulationResult(
            outcome = outcome,
            finalSnapshot = snapshot,
            replayVerified = replayVerified,
            evidence = evidence.toList(),
            eventRecords = records.toList(),
        )
    }

    private fun parseOffer(text: String): ZonedDateTime? =
        appointmentInterpreter.parseOffer(text, referenceDate)?.scheduledAt

    private companion object {
        val HARD_POLICY_REASONS = setOf(
            CallPolicyReason.TIME_UNKNOWN,
            CallPolicyReason.TIME_OUTSIDE_ALLOWED,
            CallPolicyReason.PRICE_UNKNOWN,
            CallPolicyReason.PRICE_CURRENCY_MISMATCH,
            CallPolicyReason.PRICE_ABOVE_MAX,
            CallPolicyReason.PAYMENT_MODE_UNKNOWN,
            CallPolicyReason.PAYMENT_MODE_NOT_ALLOWED,
        )
    }
}
