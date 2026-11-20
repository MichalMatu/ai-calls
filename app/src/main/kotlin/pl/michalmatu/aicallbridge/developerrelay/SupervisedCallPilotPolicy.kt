package pl.michalmatu.aicallbridge.developerrelay

/** Operator-approved GSM pilot only. No alternative privileged transport. */
internal enum class SupervisedCallPilotFailure {
    PERMISSION_REQUIRED,
    GSM_SHELL_NOT_READY,
    NO_ACTIVE_CELLULAR_CALL,
    AUDIO_ROUTE_NOT_READY,
}

internal object SupervisedCallPilotPolicy {
    /** A STOP always fences PCM before revoking the operator's authorization. */
    fun stopBeforeRevocation(stopAudio: () -> Unit, revoke: () -> Unit) {
        try { stopAudio() } finally { revoke() }
    }

    fun shouldAwaitAudioRoute(failure: SupervisedCallPilotFailure?, elapsedMs: Long): Boolean =
        failure == SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY && elapsedMs in 0L until 3_000L

    fun check(
        microphonePermission: Boolean,
        phoneStatePermission: Boolean,
        gsmShellReady: Boolean,
        hasActiveCellularCall: Boolean,
        inCallAudioMode: Boolean,
    ): SupervisedCallPilotFailure? = when {
        !microphonePermission || !phoneStatePermission -> SupervisedCallPilotFailure.PERMISSION_REQUIRED
        !gsmShellReady -> SupervisedCallPilotFailure.GSM_SHELL_NOT_READY
        !hasActiveCellularCall -> SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL
        !inCallAudioMode -> SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY
        else -> null
    }
}
