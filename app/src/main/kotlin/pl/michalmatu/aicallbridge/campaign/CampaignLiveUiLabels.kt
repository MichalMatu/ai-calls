package pl.michalmatu.aicallbridge.campaign

import pl.michalmatu.aicallbridge.R

/** Shared presentation keys for Home and Campaign. No effect or grant authority. */
internal object CampaignLiveUiLabels {
    fun title(phase: CampaignLivePhase): Int = when (phase) {
        CampaignLivePhase.INACTIVE -> R.string.camp_ux06_live_inactive
        CampaignLivePhase.PREPARING -> R.string.camp_ux06_live_preparing
        CampaignLivePhase.WAITING_FOR_PHONE -> R.string.camp_ux06_live_waiting
        CampaignLivePhase.ATTEMPT_IN_PROGRESS -> R.string.camp_ux06_live_attempt
        CampaignLivePhase.SUPERVISED_AUDIO -> R.string.camp_ux06_live_supervised
    }
}
