package pl.michalmatu.aicallbridge.developerrelay

/**
 * A pre-rendered, neutral "please wait" is optional call pacing, never a model
 * response, approval of an effect, data disclosure or condition to proceed.
 */
internal object QuickAcknowledgementPolicy {
    const val TEXT = "Proszę chwilę zaczekać."
    // The STT/model may finish immediately: avoid talking over an already-ready answer.
    const val AFTER_SPEECH_END_DELAY_MS = 300L

    fun shouldPlay(
        authorized: Boolean,
        readOnlySupervised: Boolean,
        speechDetected: Boolean,
        clipReady: Boolean,
        turn: Int,
        lastAcknowledgedTurn: Int,
        finalAudioReadyForTurn: Int,
    ): Boolean =
        authorized && !readOnlySupervised && speechDetected && clipReady &&
            turn > 0 && lastAcknowledgedTurn < turn && finalAudioReadyForTurn != turn
}
