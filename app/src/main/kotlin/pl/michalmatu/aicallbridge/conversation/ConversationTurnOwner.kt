package pl.michalmatu.aicallbridge.conversation

/** Binds a deferred speech result to one exact turn, output epoch and media generation. */
internal data class ConversationTurnOwner(val turn: Int, val epoch: Long, val generation: Long) {
    fun isCurrent(turnNow: Int, epochNow: Long, generationNow: Long, finished: Boolean): Boolean =
        !finished && turnNow == turn && epochNow == epoch && generationNow == generation
}

/** Multiple silence windows can share a turn/epoch, but not one capture authority. */
internal data class ConversationRxCaptureOwner(
    val turnOwner: ConversationTurnOwner,
    val attempt: Long,
) {
    fun isCurrent(
        turnNow: Int,
        epochNow: Long,
        generationNow: Long,
        finished: Boolean,
        attemptNow: Long,
    ): Boolean = turnOwner.isCurrent(turnNow, epochNow, generationNow, finished) &&
        attemptNow == attempt
}
