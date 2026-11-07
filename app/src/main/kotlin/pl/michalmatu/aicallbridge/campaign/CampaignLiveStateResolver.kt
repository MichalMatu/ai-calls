package pl.michalmatu.aicallbridge.campaign

/**
 * Live state is an observation of app-process runtime flags, not persisted
 * history, proof of Telecom IDLE, successful dialing or an external outcome.
 */
internal enum class CampaignLivePhase {
    INACTIVE, PREPARING, WAITING_FOR_PHONE, ATTEMPT_IN_PROGRESS, SUPERVISED_AUDIO,
}

internal object CampaignLiveStateResolver {
    fun resolve(
        supervised: Boolean,
        waitingForPhone: Boolean,
        preparingModel: Boolean,
        campaignRequested: Boolean,
        ownedAttemptRunning: Boolean,
    ): CampaignLivePhase = when {
        supervised -> CampaignLivePhase.SUPERVISED_AUDIO
        waitingForPhone -> CampaignLivePhase.WAITING_FOR_PHONE
        preparingModel -> CampaignLivePhase.PREPARING
        ownedAttemptRunning -> CampaignLivePhase.ATTEMPT_IN_PROGRESS
        campaignRequested -> CampaignLivePhase.PREPARING
        else -> CampaignLivePhase.INACTIVE
    }
}
