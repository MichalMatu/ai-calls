package pl.michalmatu.aicallbridge.identity

import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

enum class IdentitySensitivity {
    NORMAL,
    SENSITIVE,
    HIGH,
}

enum class IdentityFieldId(val sensitivity: IdentitySensitivity) {
    FIRST_NAME(IdentitySensitivity.NORMAL),
    LAST_NAME(IdentitySensitivity.NORMAL),
    PHONE(IdentitySensitivity.SENSITIVE),
    EMAIL(IdentitySensitivity.SENSITIVE),
    ADDRESS(IdentitySensitivity.SENSITIVE),
    DATE_OF_BIRTH(IdentitySensitivity.SENSITIVE),
    PESEL(IdentitySensitivity.HIGH),
}

enum class CallTaskMode {
    TEST,
    GENUINE,
}

enum class FactDisclosureDecision {
    ALLOW,
    ASK_USER,
    DENY,
}

/**
 * Per-task authorization metadata only. Identity plaintext remains in the IdentityVault boundary.
 * Collections are defensively copied so later caller mutation cannot widen disclosure authority.
 */
class AuthorizedFactSnapshot(
    val task: CallTask,
    val target: CallResolvedTarget,
    val generation: Long,
    availableFields: Set<IdentityFieldId>,
    authorizedFields: Set<IdentityFieldId>,
    highSensitivityApprovedFields: Set<IdentityFieldId> = emptySet(),
    allowedDisclosureStates: Map<IdentityFieldId, Set<TaskGraphStateId>> = emptyMap(),
) {
    val availableFields: Set<IdentityFieldId> = availableFields.toSet()
    val authorizedFields: Set<IdentityFieldId> = authorizedFields.toSet()
    val highSensitivityApprovedFields: Set<IdentityFieldId> = highSensitivityApprovedFields.toSet()
    val allowedDisclosureStates: Map<IdentityFieldId, Set<TaskGraphStateId>> =
        allowedDisclosureStates.mapValues { (_, states) -> states.toSet() }.toMap()

    init {
        require(generation >= 0L)
        require(this.highSensitivityApprovedFields.all { it in this.authorizedFields }) {
            "high-sensitivity approvals must also be task-authorized"
        }
        require(this.allowedDisclosureStates.keys.all { it in this.authorizedFields }) {
            "disclosure state scopes must reference task-authorized fields"
        }
        require(this.authorizedFields.all { this.allowedDisclosureStates[it].orEmpty().isNotEmpty() }) {
            "every task-authorized field needs at least one allowed disclosure state"
        }
    }
}

data class FactDisclosureRequest(
    val task: CallTask,
    val target: CallResolvedTarget,
    val currentState: TaskGraphStateId,
    val fieldId: IdentityFieldId,
    val mode: CallTaskMode,
    val snapshotGeneration: Long,
) {
    init { require(snapshotGeneration >= 0L) }
}

fun interface FactDisclosurePolicy {
    fun decide(
        request: FactDisclosureRequest,
        snapshot: AuthorizedFactSnapshot,
    ): FactDisclosureDecision
}

/**
 * Fail-closed baseline policy. It decides permission only; it never reads or returns vault values.
 */
class DefaultFactDisclosurePolicy : FactDisclosurePolicy {
    override fun decide(
        request: FactDisclosureRequest,
        snapshot: AuthorizedFactSnapshot,
    ): FactDisclosureDecision {
        if (
            request.task != snapshot.task ||
            request.target != snapshot.target ||
            request.snapshotGeneration != snapshot.generation
        ) {
            return FactDisclosureDecision.DENY
        }

        if (request.fieldId !in snapshot.availableFields) {
            return FactDisclosureDecision.DENY
        }

        if (
            request.mode == CallTaskMode.TEST &&
            request.fieldId.sensitivity == IdentitySensitivity.HIGH
        ) {
            return FactDisclosureDecision.DENY
        }

        if (request.fieldId !in snapshot.authorizedFields) {
            return FactDisclosureDecision.ASK_USER
        }

        if (request.currentState !in snapshot.allowedDisclosureStates.getValue(request.fieldId)) {
            return FactDisclosureDecision.DENY
        }

        if (
            request.fieldId.sensitivity == IdentitySensitivity.HIGH &&
            request.fieldId !in snapshot.highSensitivityApprovedFields
        ) {
            return FactDisclosureDecision.ASK_USER
        }

        return FactDisclosureDecision.ALLOW
    }
}
