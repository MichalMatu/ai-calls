package pl.michalmatu.aicallbridge.presentation.modelvoice

/** Off-call compatibility check state; no Android, native backend or UI dependencies. */
internal class ModelVoiceVerificationController {
    enum class BlockedReason { VOICES_UNAVAILABLE, PHONE_PERMISSION, CALL_UNSAFE }
    sealed interface Decision {
        data object Skip : Decision
        data object Start : Decision
        data class Blocked(val reason: BlockedReason, val requestPermission: Boolean) : Decision
    }
    var checking = false
        private set
    var lastAttempt: String? = null
        private set
    var checkResult: String? = null
        private set

    fun begin(
        attempt: String,
        force: Boolean,
        audio: Boolean,
        voicesReady: Boolean,
        hasPhonePermission: () -> Boolean,
        canProbe: () -> Boolean,
        blockedMessage: (BlockedReason) -> String,
    ): Decision {
        if (checking || (!force && lastAttempt == attempt)) return Decision.Skip
        // Preserve gate order. A blocked or implicit attempt never starts inference.
        val blocked = when {
            audio && !voicesReady -> BlockedReason.VOICES_UNAVAILABLE
            !hasPhonePermission() -> BlockedReason.PHONE_PERMISSION
            !canProbe() -> BlockedReason.CALL_UNSAFE
            else -> null
        }
        if (blocked != null) {
            if (force) {
                lastAttempt = attempt
                checkResult = blockedMessage(blocked)
            }
            return Decision.Blocked(blocked,
                requestPermission = force && blocked == BlockedReason.PHONE_PERMISSION)
        }
        lastAttempt = attempt
        checking = true
        checkResult = null
        return Decision.Start
    }

    fun complete(message: String) {
        checking = false
        checkResult = message
    }

    fun pause(interruptedMessage: () -> String) {
        if (checking) checkResult = interruptedMessage()
        checking = false
    }

    fun reset() {
        checking = false
        lastAttempt = null
        checkResult = null
    }
}
