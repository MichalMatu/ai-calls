package pl.michalmatu.aicallbridge.localcall

import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueHypothesis
import pl.michalmatu.aicallbridge.dialogue.ShadowDialogueObservation
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalValidation
import pl.michalmatu.aicallbridge.dialogue.SupervisorProposalValidator
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.IdentitySensitivity
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSlotValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphSnapshot
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphTransitionId

/**
 * Read-only Gate D view owned by one local call session.
 *
 * This adapter can expose bounded data to a quarantined shadow supervisor and revalidate the
 * resulting hypothesis. It deliberately has no TaskGraph reducer, effect executor, workflow,
 * speech, dialing, commitment or identity-vault API. Accepted output remains candidate data only.
 */
internal class LocalTextCallGateDRuntime(
    private val task: CallTask,
    private val graph: TaskGraphDefinition,
    private val authorizedFacts: AuthorizedFactSnapshot?,
    private val proposalValidator: SupervisorProposalValidator = SupervisorProposalValidator(),
) {
    private val taskGoal: String = "${task.action()} ${task.service()}"

    fun createShadowObservation(
        snapshot: TaskGraphSnapshot,
        finalizedTranscript: String,
        validatedNonSecretSlots: Map<TaskGraphSlotId, TaskGraphSlotValue>,
    ): ShadowDialogueObservation {
        requireBoundSnapshot(snapshot)
        return ShadowDialogueObservation(
            generation = snapshot.generation,
            taskGoal = taskGoal,
            state = snapshot.state,
            allowedTransitions = allowedTransitions(snapshot.state),
            validatedSlots = validatedNonSecretSlots,
            availableFacts = availableFactIds(snapshot.state),
            finalizedTranscript = finalizedTranscript,
        )
    }

    fun validateShadowProposal(
        observation: ShadowDialogueObservation,
        hypothesis: ShadowDialogueHypothesis,
        allowedNonSecretSlots: Set<TaskGraphSlotId>,
    ): SupervisorProposalValidation {
        requireBoundObservation(observation)
        return proposalValidator.validate(
            observation = observation,
            hypothesis = hypothesis,
            allowedNonSecretSlots = allowedNonSecretSlots,
        )
    }

    private fun requireBoundSnapshot(snapshot: TaskGraphSnapshot) {
        check(snapshot.graphVersion == graph.version) { "gate_d_graph_version_mismatch" }
        check(runCatching { graph.state(snapshot.state) }.isSuccess) { "gate_d_state_not_in_graph" }
    }

    private fun requireBoundObservation(observation: ShadowDialogueObservation) {
        check(runCatching { graph.state(observation.state) }.isSuccess) {
            "gate_d_observation_state_not_in_graph"
        }
        check(observation.taskGoal == taskGoal) { "gate_d_observation_task_mismatch" }
        check(observation.allowedTransitions == allowedTransitions(observation.state)) {
            "gate_d_observation_transition_scope_mismatch"
        }
        check(observation.availableFacts == availableFactIds(observation.state)) {
            "gate_d_observation_fact_scope_mismatch"
        }
    }

    private fun allowedTransitions(state: TaskGraphStateId): Set<TaskGraphTransitionId> =
        graph.transitions
            .asSequence()
            .filter { transition -> transition.from == state }
            .map { transition -> transition.id }
            .toSet()

    private fun availableFactIds(state: TaskGraphStateId): Set<IdentityFieldId> {
        val facts = authorizedFacts ?: return emptySet()
        return facts.authorizedFields
            .asSequence()
            .filter { field -> field in facts.availableFields }
            .filter { field -> state in facts.allowedDisclosureStates[field].orEmpty() }
            .filter { field ->
                field.sensitivity != IdentitySensitivity.HIGH ||
                    field in facts.highSensitivityApprovedFields
            }
            .toSet()
    }
}
