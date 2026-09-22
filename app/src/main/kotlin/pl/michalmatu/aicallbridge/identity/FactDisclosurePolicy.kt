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
 */
data class AuthorizedFactSnapshot(
    val task: CallTask,
    val target: CallResolvedTarget,
    val generation: Long,
    val availableFields: Set<IdentityFieldId>,
    val authorizedFields: Set<IdentityFieldId>,
    val highSensitivityApprovedFields: Set<IdentityFieldId> = emptySet(),
) {
    init {
        require(generation >= 0L)
        require(highSensitivityApprovedFields.all { it in authorizedFields }) {
            "high-sensitivity approvals must also be task-authorized"
        }
    }

    val normalizedAvailableFields: Set<IdentityFieldId> = availableFields.toSet()
    val normalizedAuthorizedFields: Set<IdentityFieldId> = authorizedFields.toSet()
    val normalizedHighSensitivityApprovedFields: Set<IdentityFieldId> =
        highSensitivityApprovedFields.toSet()
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

        if (request.fieldId !in snapshot.normalizedAvailableFields) {
            return FactDisclosureDecision.DENY
        }

        if (
            request.mode == CallTaskMode.TEST &&
            request.fieldId.sensitivity == IdentitySensitivity.HIGH
        ) {
            return FactDisclosureDecision.DENY
        }

        if (request.fieldId !in snapshot.normalizedAuthorizedFields) {
            return FactDisclosureDecision.ASK_USER
        }

        if (
            request.fieldId.sensitivity == IdentitySensitivity.HIGH &&
            request.fieldId !in snapshot.normalizedHighSensitivityApprovedFields
        ) {
            return FactDisclosureDecision.ASK_USER
        }

        return FactDisclosureDecision.ALLOW
    }
}
