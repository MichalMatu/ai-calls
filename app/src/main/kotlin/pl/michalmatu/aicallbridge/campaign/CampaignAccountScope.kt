package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import android.telephony.SubscriptionManager

internal object CampaignAccountScope {
    fun currentVoiceSubscription(context: Context): String? {
        val subscriptionId = SubscriptionManager.getDefaultVoiceSubscriptionId()
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null

        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return null
        val info = runCatching {
            manager.getActiveSubscriptionInfo(subscriptionId)
        }.getOrNull() ?: return null

        return buildString {
            append("voice-subscription:")
            append(subscriptionId)
            append(":slot:")
            append(info.simSlotIndex)
            append(":carrier:")
            append(info.carrierId)
        }
    }
}
