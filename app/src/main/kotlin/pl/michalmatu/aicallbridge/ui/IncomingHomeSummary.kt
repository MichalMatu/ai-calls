package pl.michalmatu.aicallbridge.ui

import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode

/** Read-only Home status for stored Incoming policy and its current prerequisites. */
internal object IncomingHomeSummary {
    fun resource(mode: AnsweringMode, selectedSimPresent: Boolean, hostReady: Boolean): Int = when {
        mode != AnsweringMode.AI -> R.string.home_incoming_mode_manual
        !selectedSimPresent -> R.string.home_incoming_sim_unavailable
        !hostReady -> R.string.home_incoming_audio_unavailable
        else -> R.string.home_incoming_mode_ready
    }
}
