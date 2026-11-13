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
    /**
     * Stop/TX epoch fence must become visible before the UI revokes authorization.
     * The reverse order can race the model worker and misclassify operator STOP
     * as an unrelated policy revocation. Revocation is unconditional on failure.
     */
    fun stopBeforeRevocation(stopAudio: () -> Unit, revoke: () -> Unit) {
        try { stopAudio() } finally { revoke() }
    }

    /** Bounded retry only for an already detected call waiting for Android's audio routing. */
    fun shouldAwaitAudioRoute(failure: SupervisedCallPilotFailure?, elapsedMs: Long): Boolean =
        failure == SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY && elapsedMs in 0L until 3_000L

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
