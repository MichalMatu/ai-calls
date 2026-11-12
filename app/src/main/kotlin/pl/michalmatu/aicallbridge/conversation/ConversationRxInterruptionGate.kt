package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination

/**
 * Both downlink failure and Call teardown may discover the same unfinished RX.
 * Serialize the claim *and* recording, so teardown cannot close the private
 * timeline while a winning failure callback has not yet submitted its event.
 * Ordinary completed/abandoned RX windows still use their existing CAS.
 */
internal object ConversationRxInterruptionGate {
    fun claimAndRecord(completed: AtomicBoolean, record: () -> Unit): Boolean =
        synchronized(completed) {
            if (!completed.compareAndSet(false, true)) false
            else {
                record()
                true
            }
        }

    fun sourceFailureCode(stop: ConversationStopReason?, callActive: Boolean): String =
        when {
            stop != null -> ConversationTermination.Stopped(stop).rxInterruptionCode()
            !callActive -> "OWNED_CALL_ENDED"
            else -> "SESSION_END"
        }
}
