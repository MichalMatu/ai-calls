package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision
import pl.michalmatu.aicallbridge.agent.CallProposal
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.agent.CallWorkflowState
import pl.michalmatu.aicallbridge.appointment.BookAppointmentTaskGraph
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObserver
import pl.michalmatu.aicallbridge.dialogue.ValidatedSupervisorCandidate
import pl.michalmatu.aicallbridge.taskgraph.CustomTaskGraphCore
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyBridge
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyCandidate
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyPolicy
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphApplyResult
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphCandidateProvenance
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

/**
 * One already-finalized product turn entering the application-owned Gate D integration boundary.
 *
 * Transcript text is intentionally omitted from [toString]. This object carries observation input
 * plus the already-computed deterministic proposal/policy result when the CallPlan owner produced
 * one. Those values are data only; this type owns no workflow, disclosure, output, dialing,
 * proposal approval, confirmation or commitment API.
 */
internal class GateDFinalizedTurn(
    val snapshot: TaskGraphSnapshot,
    val finalizedTranscript: String,
    val deterministicAction: CallPlanAction,
    val recoveryCount: Int,
    val deterministicProposal: CallProposal? = null,
    val deterministicPolicyDecision: CallPolicyDecision? = null,
) {
    init {
        require(recoveryCount >= 0) { "recovery_count_must_be_non_negative" }
    }

    override fun toString(): String =
        "GateDFinalizedTurn(" +
            "generation=${snapshot.generation}, " +
            "state=${snapshot.state}, " +
            "deterministicAction=$deterministicAction, " +
            "recoveryCount=$recoveryCount)"
}

/** Candidate-only deterministic product interpretation. */
internal fun interface GateDDeterministicCandidateInterpreter {
    fun interpret(turn: GateDFinalizedTurn): TaskGraphApplyCandidate?
}

/**
 * Optional one-step deterministic follow-up after an accepted deterministic apply.
 *
 * This seam consumes only already-computed application-owner data plus the accepted reducer result.
 * It may return at most one candidate, which is sent through the same [TaskGraphApplyBridge] and
 * current authorization checks as every other product candidate. It is not a generic effect
 * executor and cannot call workflow, confirmation, commitment, disclosure, speech or dialing APIs.
 */
internal fun interface GateDDeterministicFollowUpRouter {
    fun route(
        turn: GateDFinalizedTurn,
        accepted: TaskGraphApplyResult.Accepted,
    ): TaskGraphApplyCandidate?
}

/** Recomputed application-owned slot authorization for the supplied current snapshot. */
internal fun interface GateDAuthorizedSlotIdsProvider {
    fun authorizedSlotIds(snapshot: TaskGraphSnapshot): Set<TaskGraphSlotId>
}

/**
 * Receives an apply result, including effects as inert data. Implementations remain responsible for
 * routing those effects to existing owners; this is deliberately not a generic effect executor.
 */
internal fun interface GateDTaskGraphApplyResultListener {
    fun onApplyResult(result: TaskGraphApplyResult)
}

/**
 * Explicit reviewed Gate D product wiring. The public Android session path does not create one.
 *
 * A deterministic candidate is attempted first. Shadow is optional and may run only when the
 * deterministic interpreter returns no candidate. A deterministic rejection therefore fails closed
 * rather than falling through to a supervisor proposal. An optional deterministic follow-up may run
 * once only after the first deterministic apply is accepted.
 */
internal class LocalTextCallGateDProductBinding(
    val deterministicInterpreter: GateDDeterministicCandidateInterpreter,
    val applyPolicy: TaskGraphApplyPolicy,
    val authorizedSlotIdsProvider: GateDAuthorizedSlotIdsProvider,
    val applyResultListener: GateDTaskGraphApplyResultListener,
    val shadowObserver: ShadowDialogueObserver? = null,
    val shadowExecutor: GateDShadowExecutor? = null,
    val shadowDiagnosticsListener: GateDShadowDiagnosticsListener =
        GateDShadowDiagnosticsListener { _ -> },
    val deterministicFollowUpRouter: GateDDeterministicFollowUpRouter? = null,
) {
    init {
        require((shadowObserver == null) == (shadowExecutor == null)) {
            "gate_d_shadow_observer_and_executor_must_be_bound_together"
        }
    }
}

/**
 * Application-owned Gate D integration for one local call session.
 *
 * Order is fixed: deterministic interpretation -> optional one-step deterministic follow-up ->
 * optional bounded shadow -> existing supervisor validation -> current-state/authorization re-check
 * -> [TaskGraphApplyBridge]. Accepted reducer effects remain inert result data. This class has no
 * workflow, speech/TTS, dialing, identity-vault, proposal approval, user-confirmation, commitment or
 * completion authority. The one BOOK_APPOINTMENT user-decision method below is an explicit reviewed
 * composition boundary: it reuses CallWorkflow as the decision owner and still creates no
 * commitment authorization.
 */
internal class LocalTextCallGateDProductIntegration(
    runtime: LocalTextCallGateDRuntime,
    definition: TaskGraphDefinition,
    initialSnapshot: TaskGraphSnapshot,
    maxRecoveryCount: Int,
    private val binding: LocalTextCallGateDProductBinding,
) : AutoCloseable {
    private val lock = Any()
    private val applyBridge = TaskGraphApplyBridge(
        definition = definition,
        core = CustomTaskGraphCore(definition),
        policy = binding.applyPolicy,
    )
    private var currentSnapshot = initialSnapshot
    private var cancelled = false
    private var closed = false

    private val shadowLifecycle: LocalTextCallGateDShadowLifecycle? =
        binding.shadowObserver?.let { observer ->
            LocalTextCallGateDShadowLifecycle(
                runtime = runtime,
                authoritativeSnapshotProvider = { currentSnapshotForShadow() },
                maxRecoveryCount = maxRecoveryCount,
                observer = observer,
                executor = checkNotNull(binding.shadowExecutor),
                diagnosticsListener = binding.shadowDiagnosticsListener,
                allowedNonSecretSlotsProvider = { snapshot ->
                    binding.authorizedSlotIdsProvider.authorizedSlotIds(snapshot).toSet()
                },
                validatedCandidateListener = { candidate ->
                    applySupervisorCandidate(candidate)
                },
            )
        }

    init {
        require(initialSnapshot.graphVersion == definition.version) {
            "gate_d_initial_graph_version_mismatch"
        }
        require(runCatching { definition.state(initialSnapshot.state) }.isSuccess) {
            "gate_d_initial_state_not_in_graph"
        }
    }

    fun onFinalizedTurn(
        finalizedTranscript: String,
        deterministicAction: CallPlanAction,
        recoveryCount: Int,
        deterministicProposal: CallProposal? = null,
        deterministicPolicyDecision: CallPolicyDecision? = null,
    ) {
        val snapshot = currentSnapshotOrNull() ?: return
        val turn = GateDFinalizedTurn(
            snapshot = snapshot,
            finalizedTranscript = finalizedTranscript,
            deterministicAction = deterministicAction,
            recoveryCount = recoveryCount,
            deterministicProposal = deterministicProposal,
            deterministicPolicyDecision = deterministicPolicyDecision,
        )
        val candidate = try {
            binding.deterministicInterpreter.interpret(turn)
        } catch (_: Throwable) {
            // Deterministic interpretation failure is fail-closed; shadow must not bypass it.
            return
        }

        if (candidate != null) {
            if (candidate.provenance != TaskGraphCandidateProvenance.DETERMINISTIC) {
                return
            }
            val accepted = applyCandidate(candidate) as? TaskGraphApplyResult.Accepted ?: return
            val followUp = try {
                binding.deterministicFollowUpRouter?.route(turn, accepted)
            } catch (_: Throwable) {
                // Deterministic follow-up failure is fail-closed; it cannot fall through to shadow.
                return
            }
            if (followUp != null) {
                if (followUp.provenance != TaskGraphCandidateProvenance.DETERMINISTIC) {
                    return
                }
                applyCandidate(followUp)
            }
            return
        }

        shadowLifecycle?.onFinalizedTurn(
            finalizedTranscript = finalizedTranscript,
            deterministicAction = deterministicAction,
            recoveryCount = recoveryCount,
        )
    }

    /**
     * Applies one explicit application-owned BOOK_APPOINTMENT user decision.
     *
     * The graph transition is first staged through the same apply bridge while holding the product
     * lock, but [currentSnapshot] is not changed. Only after graph/state/generation/mapping and slot
     * authorization checks pass does the existing CallWorkflow owner consume the exact pending
     * proposal. The staged graph snapshot is committed only after that owner mutation succeeds.
     * No commitment permit is created or consumed here.
     */
    fun applyBookAppointmentUserDecision(
        workflow: CallWorkflow,
        decision: GateDBookAppointmentUserDecision,
    ): GateDBookAppointmentUserDecisionResult {
        var resultToNotify: TaskGraphApplyResult? = null
        val result = synchronized(lock) {
            if (closed || cancelled) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.INTEGRATION_INACTIVE,
                )
            }

            val snapshot = currentSnapshot
            if (snapshot.state != BookAppointmentTaskGraph.CONFIRMATION) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.GRAPH_NOT_CONFIRMATION,
                )
            }

            val workflowSnapshot = workflow.snapshot()
            if (workflowSnapshot.state() != CallWorkflowState.NEEDS_USER_DECISION) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.WORKFLOW_NOT_WAITING_FOR_USER,
                )
            }
            val pendingProposal = workflowSnapshot.pendingProposal()
                ?: return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.MISSING_WORKFLOW_PROPOSAL,
                )
            val graphAppointment = snapshot.context[BookAppointmentTaskGraph.APPOINTMENT_AT]
                as? TaskGraphSlotValue.Text
                ?: return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.MISSING_GRAPH_APPOINTMENT,
                )
            val pendingScheduledAt = pendingProposal.scheduledAt()
            if (pendingScheduledAt == null || graphAppointment.value != pendingScheduledAt.toString()) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.PROPOSAL_MISMATCH,
                )
            }

            val authorizedSlotIds = try {
                binding.authorizedSlotIdsProvider.authorizedSlotIds(snapshot).toSet()
            } catch (_: Throwable) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.AUTHORIZATION_RECHECK_FAILED,
                )
            }
            val transitionId = when (decision) {
                GateDBookAppointmentUserDecision.CONFIRM -> BOOK_APPOINTMENT_CONFIRM_TRANSITION
                GateDBookAppointmentUserDecision.REJECT -> BOOK_APPOINTMENT_REJECT_TRANSITION
            }
            val staged = applyBridge.apply(
                snapshot = snapshot,
                candidate = TaskGraphApplyCandidate.deterministic(
                    generation = snapshot.generation,
                    transitionId = transitionId,
                ),
                authorizedSlotIds = authorizedSlotIds,
            )
            if (staged !is TaskGraphApplyResult.Accepted) {
                resultToNotify = staged
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.GRAPH_APPLY_REJECTED,
                )
            }
            if (staged.effects.isNotEmpty()) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.UNEXPECTED_EFFECTS,
                )
            }
            val expectedState = when (decision) {
                GateDBookAppointmentUserDecision.CONFIRM -> BookAppointmentTaskGraph.COMMITMENT
                GateDBookAppointmentUserDecision.REJECT -> BookAppointmentTaskGraph.WAITING_OFFER
            }
            if (staged.snapshot.state != expectedState) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.UNEXPECTED_GRAPH_RESULT,
                )
            }

            val ownerProposal = try {
                when (decision) {
                    GateDBookAppointmentUserDecision.CONFIRM -> workflow.approvePendingProposal()
                    GateDBookAppointmentUserDecision.REJECT -> workflow.rejectPendingProposal()
                }
            } catch (_: Throwable) {
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.WORKFLOW_DECISION_FAILED,
                )
            }
            if (ownerProposal != pendingProposal) {
                cancelled = true
                return@synchronized rejected(
                    GateDBookAppointmentUserDecisionRejectReason.OWNER_PROPOSAL_MISMATCH,
                )
            }

            currentSnapshot = staged.snapshot
            resultToNotify = staged
            GateDBookAppointmentUserDecisionResult.Applied(
                state = staged.snapshot.state,
                generation = staged.snapshot.generation,
            )
        }

        resultToNotify?.let(::notifyApplyResult)
        return result
    }

    fun cancel() {
        synchronized(lock) {
            if (!closed) cancelled = true
        }
        shadowLifecycle?.cancel()
    }

    override fun close() {
        val shouldClose = synchronized(lock) {
            if (closed) {
                false
            } else {
                closed = true
                cancelled = true
                true
            }
        }
        if (shouldClose) shadowLifecycle?.close()
    }

    private fun applySupervisorCandidate(candidate: ValidatedSupervisorCandidate) {
        applyCandidate(TaskGraphApplyCandidate.fromSupervisor(candidate))
    }

    private fun applyCandidate(candidate: TaskGraphApplyCandidate): TaskGraphApplyResult? {
        val result = synchronized(lock) {
            if (closed || cancelled) return@synchronized null
            val snapshot = currentSnapshot
            val authorizedSlotIds = try {
                binding.authorizedSlotIdsProvider.authorizedSlotIds(snapshot).toSet()
            } catch (_: Throwable) {
                return@synchronized null
            }
            applyBridge.apply(
                snapshot = snapshot,
                candidate = candidate,
                authorizedSlotIds = authorizedSlotIds,
            ).also { applyResult ->
                if (applyResult is TaskGraphApplyResult.Accepted) {
                    currentSnapshot = applyResult.snapshot
                }
            }
        } ?: return null

        notifyApplyResult(result)
        return result
    }

    private fun notifyApplyResult(result: TaskGraphApplyResult) {
        try {
            binding.applyResultListener.onApplyResult(result)
        } catch (_: Throwable) {
            // Result observation cannot change deterministic routing or gain execution authority.
        }
    }

    private fun rejected(
        reason: GateDBookAppointmentUserDecisionRejectReason,
    ): GateDBookAppointmentUserDecisionResult.Rejected =
        GateDBookAppointmentUserDecisionResult.Rejected(reason)

    private fun currentSnapshotOrNull(): TaskGraphSnapshot? = synchronized(lock) {
        if (closed || cancelled) null else currentSnapshot
    }

    private fun currentSnapshotForShadow(): TaskGraphSnapshot = synchronized(lock) {
        check(!closed && !cancelled) { "gate_d_product_integration_inactive" }
        currentSnapshot
    }

    private companion object {
        val BOOK_APPOINTMENT_CONFIRM_TRANSITION = TaskGraphTransitionId("confirm-proposal")
        val BOOK_APPOINTMENT_REJECT_TRANSITION = TaskGraphTransitionId("reject-proposal")
    }
}
