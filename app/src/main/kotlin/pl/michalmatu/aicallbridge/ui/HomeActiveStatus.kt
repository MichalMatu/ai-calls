package pl.michalmatu.aicallbridge.ui

import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.campaign.CampaignLivePhase
import pl.michalmatu.aicallbridge.campaign.CampaignLiveUiLabels

/** A display-only interpretation of live app-owned activity, not Telecom call state. */
internal data class HomeActiveState(val label: Int, val showStop: Boolean)

internal object HomeActiveStatus {
    fun resolve(phase: CampaignLivePhase, incomingCallActive: Boolean,
        outgoingTaskActive: Boolean): HomeActiveState = when {
        incomingCallActive -> HomeActiveState(R.string.home_activity_incoming, true)
        phase != CampaignLivePhase.INACTIVE ->
            HomeActiveState(CampaignLiveUiLabels.title(phase), true)
        outgoingTaskActive -> HomeActiveState(R.string.home_activity_outgoing, true)
        else -> HomeActiveState(R.string.home_activity_idle, false)
    }
}
