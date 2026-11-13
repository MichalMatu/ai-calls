package pl.michalmatu.aicallbridge.textagent

import java.util.concurrent.CancellationException

/** Model-window data only; the full private call archive is owned elsewhere. */
internal data class NativeConversationTurn(val userText: String, val modelText: String) {
    override fun toString() = "NativeConversationTurn(REDACTED)"
}

internal class NativeConversationWindow {
    private val turns = ArrayDeque<NativeConversationTurn>()
    var completedTurns: Int = 0
        private set

    @Synchronized fun snapshot(): List<NativeConversationTurn> = turns.toList()
    @Synchronized fun remember(userText: String, modelText: String) {
        turns.addLast(NativeConversationTurn(userText, modelText))
        completedTurns++
    }
    @Synchronized fun dropOldest(): Boolean {
        if (turns.isEmpty()) return false
        turns.removeFirst()
        return true
    }
    @Synchronized fun clear() { turns.clear(); completedTurns = 0 }
}

/** Retry inference only. No speech, tool, grant, Telecom or external action is dispatched here. */
internal fun <C, R> sendWithContextRecovery(
    initial: C,
    window: NativeConversationWindow,
    isCurrent: () -> Boolean,
    replaceConversation: (C) -> C,
    send: (C) -> R,
): R {
    var conversation = initial
    while (true) {
        if (!isCurrent()) throw CancellationException("native_generation_cancelled")
        try {
            return send(conversation)
        } catch (error: Throwable) {
            if (!isCurrent()) throw CancellationException("native_generation_cancelled")
            // Each retry removes one completed user/model pair. Never shorten the
            // current utterance or system instruction, and never impose a call-turn quota.
            var failure = error
            while (true) {
                if (!isCurrent()) throw CancellationException("native_generation_cancelled")
                if (!isNativeContextCapacityFailure(failure) || !window.dropOldest()) throw failure
                try {
                    conversation = replaceConversation(conversation)
                    break
                } catch (recreationFailure: Throwable) {
                    failure = recreationFailure
                }
            }
        }
    }
}

internal fun isNativeContextCapacityFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        val message = current.message.orEmpty()
        if ("Chosen prefill work group size exceeds available state entries" in message ||
            ("RESOURCE_EXHAUSTED" in message &&
                ("context window" in message.lowercase() || "number of tokens" in message.lowercase())))
            return true
        current = current.cause
    }
    return false
}
