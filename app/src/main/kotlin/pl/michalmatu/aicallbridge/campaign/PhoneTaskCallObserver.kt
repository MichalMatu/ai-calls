package pl.michalmatu.aicallbridge.campaign

/** Evidence seam on the existing owned-call executor; grants and Call ownership remain there. */
internal interface PhoneTaskCallObserver {
    fun sessionStarted(sessionId: String)
    fun transcript(sessionId: String, turn: Int, text: String)
    fun unanswered(outcome: PhoneAttemptOutcome)
}
