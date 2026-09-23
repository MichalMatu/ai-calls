package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPolicyDecision
import pl.michalmatu.aicallbridge.agent.CallProposal
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
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot

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
 * completion authority.
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

        try {
            binding.applyResultListener.onApplyResult(result)
        } catch (_: Throwable) {
            // Result observation cannot change deterministic routing or gain execution authority.
        }
        return result
    }

    private fun currentSnapshotOrNull(): TaskGraphSnapshot? = synchronized(lock) {
        if (closed || cancelled) null else currentSnapshot
    }

    private fun currentSnapshotForShadow(): TaskGraphSnapshot = synchronized(lock) {
        check(!closed && !cancelled) { "gate_d_product_integration_inactive" }
        currentSnapshot
    }
}
