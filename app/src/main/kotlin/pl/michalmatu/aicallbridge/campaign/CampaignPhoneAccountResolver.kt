package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Exact selected default voice SIM; other installed/available SIMs are not a veto. */
internal object CampaignPhoneAccountResolver {
    fun resolveDefaultVoiceAccount(
        context: Context,
        telecomManager: TelecomManager,
    ): PhoneAccountHandle? = runCatching {
        val handle = telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            ?: return@runCatching null
        if (handle !in telecomManager.callCapablePhoneAccounts) return@runCatching null
        val telephony = context.getSystemService(TelephonyManager::class.java)
            ?: return@runCatching null
        val voice = telephony.createForPhoneAccountHandle(handle)
            ?: return@runCatching null
        if (!accountMatchesDefault(
                candidateSubscriptionId = voice.subscriptionId,
                defaultVoiceSubscriptionId = SubscriptionManager.getDefaultVoiceSubscriptionId(),
            )
        ) return@runCatching null
        handle
    }.getOrNull()

    fun accountMatchesDefault(
        candidateSubscriptionId: Int,
        defaultVoiceSubscriptionId: Int,
    ): Boolean = candidateSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
        candidateSubscriptionId == defaultVoiceSubscriptionId
}
