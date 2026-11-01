package pl.michalmatu.aicallbridge.campaign

import android.content.Intent
import android.telecom.PhoneAccountHandle

/**
 * A real platform-specific controller owns the actual outgoing call and its exact hangup.
 * ACTION_CALL + global isInCall()/endCall() never provides such ownership.
 */
internal interface CampaignOwnedCallSession {
    fun isExactCallActive(): Boolean
    fun disconnectExactCall(): Boolean
}

internal interface CampaignOwnedCallController {
    /** Must return a handle for this exact outgoing call, not the device's foreground call. */
    fun dial(intent: Intent, account: PhoneAccountHandle, target: String): CampaignOwnedCallSession
}

/**
 * Integration point for a verified call-specific Android backend. Not a boolean proof.
 * No such backend is present in the stock Android implementation at this checkpoint.
 * A future module must supply it together with physical acceptance before real dialing.
 */
internal object CampaignCallControllerRegistry {
    @Volatile private var provider: CampaignOwnedCallController? = null

    fun available(): CampaignOwnedCallController? = provider

    internal fun installVerified(controller: CampaignOwnedCallController) {
        check(provider == null) { "owned_call_controller_already_installed" }
        provider = controller
    }
}
