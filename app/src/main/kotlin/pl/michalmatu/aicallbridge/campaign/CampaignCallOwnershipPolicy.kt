package pl.michalmatu.aicallbridge.campaign

/**
 * Generic TelecomManager.isInCall/endCall are not call-specific capabilities.
 * A future call owner must supply both exact identity and targeted termination.
 */
internal data class CampaignCallOwnershipProof(
    val exactCallIdentityBound: Boolean,
    val targetedTerminationAvailable: Boolean,
)

internal object CampaignCallOwnershipPolicy {
    fun allowsAutonomousDial(proof: CampaignCallOwnershipProof): Boolean =
        proof.exactCallIdentityBound && proof.targetedTerminationAvailable
}
