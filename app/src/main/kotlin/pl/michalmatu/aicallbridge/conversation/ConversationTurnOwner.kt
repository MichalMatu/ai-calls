package pl.michalmatu.aicallbridge.conversation

/** Binds a deferred speech result to one exact turn, output epoch and media generation. */
internal data class ConversationTurnOwner(val turn: Int, val epoch: Long, val generation: Long) {
    fun isCurrent(turnNow: Int, epochNow: Long, generationNow: Long, finished: Boolean): Boolean =
        !finished && turnNow == turn && epochNow == epoch && generationNow == generation
}
