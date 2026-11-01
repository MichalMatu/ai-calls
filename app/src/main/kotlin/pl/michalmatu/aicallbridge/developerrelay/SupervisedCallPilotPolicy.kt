package pl.michalmatu.aicallbridge.developerrelay

/**
 * The supervised pilot is deliberately separate from dialing authorization.
 * The operator starts and ends the cellular call in the default Phone app.
 */
internal enum class SupervisedCallPilotFailure {
    PERMISSION_REQUIRED,
    SHIZUKU_NOT_READY,
    NO_ACTIVE_CELLULAR_CALL,
    AUDIO_ROUTE_NOT_READY,
}

internal object SupervisedCallPilotPolicy {
    fun check(
        microphonePermission: Boolean,
        phoneStatePermission: Boolean,
        shizukuReady: Boolean,
        hasActiveCellularCall: Boolean,
        inCallAudioMode: Boolean,
    ): SupervisedCallPilotFailure? = when {
        !microphonePermission || !phoneStatePermission -> SupervisedCallPilotFailure.PERMISSION_REQUIRED
        !shizukuReady -> SupervisedCallPilotFailure.SHIZUKU_NOT_READY
        !hasActiveCellularCall -> SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL
        !inCallAudioMode -> SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY
        else -> null
    }
}
