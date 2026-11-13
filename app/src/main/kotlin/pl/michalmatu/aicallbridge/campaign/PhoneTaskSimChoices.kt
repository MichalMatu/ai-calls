package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import pl.michalmatu.aicallbridge.R

/** One ordered SIM list for the wizard and its private diagnostic view. */
internal data class PhoneTaskSimChoice(
    val subscriptionId: Int,
    val scope: String,
    val slotNumber: Int,
    val displayName: String,
) {
    fun label(context: Context): String =
        context.getString(R.string.scenario_call_sim_option, slotNumber, displayName)
    override fun toString() = "PhoneTaskSimChoice(slot=$slotNumber,scope=REDACTED)"
}

internal object PhoneTaskSimChoices {
    fun available(context: Context): List<PhoneTaskSimChoice> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList.orEmpty().mapNotNull { info ->
                    CampaignAccountScope.forSubscription(context, info.subscriptionId)?.let { scope ->
                        PhoneTaskSimChoice(info.subscriptionId, scope, info.simSlotIndex + 1,
                            info.displayName.toString())
                    }
                }
        }.getOrDefault(emptyList())
    }

    /** A disappeared or changed saved SIM never silently becomes a different SIM. */
    fun selectedIndex(choices: List<PhoneTaskSimChoice>, savedId: Int?, savedScope: String?,
        defaultVoiceId: Int): Int {
        if (savedId != null || savedScope != null) {
            return choices.indexOfFirst {
                it.subscriptionId == savedId && it.scope == savedScope
            } + 1
        }
        val default = choices.indexOfFirst { it.subscriptionId == defaultVoiceId }
        return if (default >= 0) default + 1 else if (choices.size == 1) 1 else 0
    }
}
