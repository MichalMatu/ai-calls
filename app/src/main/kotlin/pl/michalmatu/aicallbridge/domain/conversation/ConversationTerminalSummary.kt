package pl.michalmatu.aicallbridge.domain.conversation

import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination

/**
 * Pure terminal evidence projection. Bytes indicate observed pipeline transport
 * only, never proof of carrier playback, external effects or a successful booking.
 * The runtime sanitizes reason codes before entering this boundary.
 */
internal object ConversationTerminalSummary {
    fun entries(
        terminal: ConversationTermination,
        turnsCompleted: Int,
        receivedBytes: Long,
        interruptedReceivedBytes: Long,
        transmittedBytes: Long,
        safeReason: String,
    ): List<String> = buildList {
        add("turns_completed=$turnsCompleted")
        add("telephony_rx_pcm_bytes=$receivedBytes")
        add("telephony_rx_partial_pcm_bytes=$interruptedReceivedBytes")
        add("telephony_tx_pcm_bytes=$transmittedBytes")
        add("media_rx_tx_observed=" +
            (turnsCompleted > 0 && receivedBytes > 0 && transmittedBytes > 0))
        add("conversation_terminal_category=" + when (terminal) {
            ConversationTermination.Completed -> "completed"
            is ConversationTermination.Stopped -> "stopped"
            is ConversationTermination.Failed -> "failed"
        })
        add("chat_relay_live_call_success=${terminal == ConversationTermination.Completed}")
        add("conversation_end_reason=$safeReason")
        if (terminal is ConversationTermination.Failed) add("failure_reason=$safeReason")
    }
}
