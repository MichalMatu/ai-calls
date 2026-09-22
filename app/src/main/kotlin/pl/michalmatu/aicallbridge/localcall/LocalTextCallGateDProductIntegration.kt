package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
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
 * only; it owns no workflow, disclosure, output, dialing, proposal, confirmation or commitment API.
 */
internal class GateDFinalizedTurn(
    val snapshot: TaskGraphSnapshot,
    val finalizedTranscript: String,
    val deterministicAction: CallPlanAction,
    val recoveryCount: Int,
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
 * rather than falling through to a supervisor proposal.
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
 * Order is fixed: deterministic interpretation -> optional bounded shadow -> existing supervisor
 * validation -> current-state/authorization re-check -> [TaskGraphApplyBridge]. Accepted reducer
 * effects remain inert result data. This class has no workflow, speech/TTS, dialing, identity-vault,
 * proposal approval, user-confirmation, commitment or completion authority.
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
    ) {
        val snapshot = currentSnapshotOrNull() ?: return
        val candidate = try {
            binding.deterministicInterpreter.interpret(
                GateDFinalizedTurn(
                    snapshot = snapshot,
                    finalizedTranscript = finalizedTranscript,
                    deterministicAction = deterministicAction,
                    recoveryCount = recoveryCount,
                ),
            )
        } catch (_: Throwable) {
            // Deterministic interpretation failure is fail-closed; shadow must not bypass it.
            return
        }

        if (candidate != null) {
            if (candidate.provenance != TaskGraphCandidateProvenance.DETERMINISTIC) {
                return
            }
            applyCandidate(candidate)
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

    private fun applyCandidate(candidate: TaskGraphApplyCandidate) {
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
        } ?: return

        try {
            binding.applyResultListener.onApplyResult(result)
        } catch (_: Throwable) {
            // Result observation cannot change deterministic routing or gain execution authority.
        }
    }

    private fun currentSnapshotOrNull(): TaskGraphSnapshot? = synchronized(lock) {
        if (closed || cancelled) null else currentSnapshot
    }

    private fun currentSnapshotForShadow(): TaskGraphSnapshot = synchronized(lock) {
        check(!closed && !cancelled) { "gate_d_product_integration_inactive" }
        currentSnapshot
    }
}
