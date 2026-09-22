package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.dialogue.DefaultDialogueFitPolicy
import pl.michalmatu.aicallbridge.dialogue.DeterministicUnderstanding
import pl.michalmatu.aicallbridge.dialogue.DialogueFitResult
import pl.michalmatu.aicallbridge.dialogue.DialogueFitSignals
import pl.michalmatu.aicallbridge.dialogue.DialogueSttQuality
import pl.michalmatu.aicallbridge.dialogue.ParserCompleteness
import pl.michalmatu.aicallbridge.dialogue.ShadowComparison
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObserver
import pl.michalmatu.aicallbridge.dialogue.StateCompatibility
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalRejectReason
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalValidation
import pl.michalmatu.aicallbridge.dialogue.ValidatedSupervisorCandidate
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

internal interface GateDShadowExecutor : AutoCloseable {
    fun execute(block: () -> Unit)
}

internal fun interface GateDShadowDiagnosticsListener {
    fun onDiagnostics(diagnostics: GateDShadowTurnDiagnostics)
}

internal enum class GateDShadowValidationStatus {
    ACCEPTED,
    REJECTED,
}

/**
 * Safe ordinary diagnostics for one quarantined Gate D shadow turn.
 *
 * Transcript text, task/fact values, slot candidate values and model diagnostic values are
 * deliberately absent. Candidate data is represented only by typed IDs and validation status.
 */
internal class GateDShadowTurnDiagnostics(
    val observationGeneration: Long,
    val hypothesisGeneration: Long,
    val suggestedTransition: TaskGraphTransitionId?,
    candidateSlotIds: Set<TaskGraphSlotId>,
    val validationStatus: GateDShadowValidationStatus,
    val rejectReason: SupervisorProposalRejectReason?,
    val dialogueFit: DialogueFitResult,
) {
    val candidateSlotIds: Set<TaskGraphSlotId> = candidateSlotIds.toSet()

    init {
        require(observationGeneration >= 0L)
        require(hypothesisGeneration >= 0L)
        require((validationStatus == GateDShadowValidationStatus.REJECTED) == (rejectReason != null))
    }

    override fun toString(): String =
        "GateDShadowTurnDiagnostics(" +
            "observationGeneration=$observationGeneration, " +
            "hypothesisGeneration=$hypothesisGeneration, " +
            "suggestedTransition=$suggestedTransition, " +
            "candidateSlotIds=$candidateSlotIds, " +
            "validationStatus=$validationStatus, " +
            "rejectReason=$rejectReason, " +
            "dialogueFit=$dialogueFit)"
}

/**
 * Session-owned host shadow lifecycle. Every finalized turn receives a monotonically increasing
 * session epoch. Newer turns, cancel and close invalidate queued work before observer output can be
 * revalidated or surfaced as diagnostics. No TaskGraph reducer or application authority exists
 * here. An optional validated-candidate callback still receives candidate data only.
 */
internal class LocalTextCallGateDShadowLifecycle internal constructor(
    private val runtime: LocalTextCallGateDRuntime,
    private val authoritativeSnapshotProvider: () -> TaskGraphSnapshot,
    maxRecoveryCount: Int,
    private val observer: ShadowDialogueObserver,
    private val executor: GateDShadowExecutor,
    private val diagnosticsListener: GateDShadowDiagnosticsListener,
    private val allowedNonSecretSlotsProvider: ((TaskGraphSnapshot) -> Set<TaskGraphSlotId>)?,
    private val validatedCandidateListener: ((ValidatedSupervisorCandidate) -> Unit)?,
) : AutoCloseable {
    private val lock = Any()
    private val fitPolicy = DefaultDialogueFitPolicy(maxRecoveryCount.coerceAtLeast(1))
    private var epoch = 0L
    private var closed = false

    constructor(
        runtime: LocalTextCallGateDRuntime,
        authoritativeSnapshot: TaskGraphSnapshot,
        maxRecoveryCount: Int,
        observer: ShadowDialogueObserver,
        executor: GateDShadowExecutor,
        diagnosticsListener: GateDShadowDiagnosticsListener,
    ) : this(
        runtime = runtime,
        authoritativeSnapshotProvider = { authoritativeSnapshot },
        maxRecoveryCount = maxRecoveryCount,
        observer = observer,
        executor = executor,
        diagnosticsListener = diagnosticsListener,
        allowedNonSecretSlotsProvider = null,
        validatedCandidateListener = null,
    )

    fun onFinalizedTurn(
        finalizedTranscript: String,
        deterministicAction: CallPlanAction,
        recoveryCount: Int,
    ) {
        val turnEpoch = synchronized(lock) {
            if (closed) return
            epoch += 1L
            epoch
        }
        val authoritativeSnapshot = try {
            authoritativeSnapshotProvider()
        } catch (_: Throwable) {
            return
        }
        if (!isCurrent(turnEpoch)) return
        val observation = try {
            runtime.createShadowObservation(
                snapshot = authoritativeSnapshot,
                finalizedTranscript = finalizedTranscript,
            )
        } catch (_: Throwable) {
            return
        }

        executor.execute {
            if (!isCurrent(turnEpoch)) return@execute
            val hypothesis = try {
                observer.observe(observation)
            } catch (_: Throwable) {
                return@execute
            }
            if (!isCurrent(turnEpoch)) return@execute

            val allowedNonSecretSlots = try {
                allowedNonSecretSlotsProvider?.invoke(authoritativeSnapshot)?.toSet()
                    ?: observation.validatedSlots.keys
            } catch (_: Throwable) {
                return@execute
            }
            if (!isCurrent(turnEpoch)) return@execute

            val validation = try {
                runtime.validateShadowProposal(
                    observation = observation,
                    hypothesis = hypothesis,
                    allowedNonSecretSlots = allowedNonSecretSlots,
                )
            } catch (_: Throwable) {
                return@execute
            }
            if (!isCurrent(turnEpoch)) return@execute

            val diagnostics = buildDiagnostics(
                observationGeneration = observation.generation,
                hypothesisGeneration = hypothesis.generation,
                suggestedTransition = hypothesis.suggestedTransition,
                candidateSlotIds = hypothesis.slotCandidates.keys,
                validation = validation,
                deterministicAction = deterministicAction,
                recoveryCount = recoveryCount,
                transitionCompatible = hypothesis.suggestedTransition in observation.allowedTransitions,
            )
            if (!isCurrent(turnEpoch)) return@execute

            if (validation is SupervisorProposalValidation.Accepted) {
                try {
                    validatedCandidateListener?.invoke(validation.candidate)
                } catch (_: Throwable) {
                    // Candidate consumers remain outside this shadow lifecycle's authority.
                }
            }
            if (!isCurrent(turnEpoch)) return@execute
            diagnosticsListener.onDiagnostics(diagnostics)
        }
    }

    fun cancel() {
        synchronized(lock) {
            if (!closed) epoch += 1L
        }
    }

    override fun close() {
        val shouldClose = synchronized(lock) {
            if (closed) {
                false
            } else {
                closed = true
                epoch += 1L
                true
            }
        }
        if (shouldClose) executor.close()
    }

    private fun isCurrent(turnEpoch: Long): Boolean = synchronized(lock) {
        !closed && epoch == turnEpoch
    }

    private fun buildDiagnostics(
        observationGeneration: Long,
        hypothesisGeneration: Long,
        suggestedTransition: TaskGraphTransitionId?,
        candidateSlotIds: Set<TaskGraphSlotId>,
        validation: SupervisorProposalValidation,
        deterministicAction: CallPlanAction,
        recoveryCount: Int,
        transitionCompatible: Boolean,
    ): GateDShadowTurnDiagnostics {
        val status: GateDShadowValidationStatus
        val rejectReason: SupervisorProposalRejectReason?
        when (validation) {
            is SupervisorProposalValidation.Accepted -> {
                status = GateDShadowValidationStatus.ACCEPTED
                rejectReason = null
            }
            is SupervisorProposalValidation.Rejected -> {
                status = GateDShadowValidationStatus.REJECTED
                rejectReason = validation.reason
            }
        }

        val fit = fitPolicy.evaluate(
            DialogueFitSignals(
                sttQuality = DialogueSttQuality.UNAVAILABLE,
                deterministicUnderstanding = if (deterministicAction == CallPlanAction.SAY) {
                    DeterministicUnderstanding.MATCHED
                } else {
                    DeterministicUnderstanding.UNKNOWN
                },
                parserCompleteness = ParserCompleteness.NOT_APPLICABLE,
                stateCompatibility = if (transitionCompatible) {
                    StateCompatibility.COMPATIBLE
                } else {
                    StateCompatibility.INCOMPATIBLE
                },
                contradictionDetected = false,
                missingRequiredSlots = 0,
                recoveryCount = recoveryCount,
                shadowComparison = ShadowComparison.NOT_AVAILABLE,
            ),
        )
        return GateDShadowTurnDiagnostics(
            observationGeneration = observationGeneration,
            hypothesisGeneration = hypothesisGeneration,
            suggestedTransition = suggestedTransition,
            candidateSlotIds = candidateSlotIds,
            validationStatus = status,
            rejectReason = rejectReason,
            dialogueFit = fit,
        )
    }
}
