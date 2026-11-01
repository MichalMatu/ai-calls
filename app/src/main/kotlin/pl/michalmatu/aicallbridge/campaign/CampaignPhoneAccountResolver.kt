package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    ): PhoneAccountHandle? {
        // Subscription ID for a phone-account-specific TelephonyManager requires
        // API 30. On API 29 we cannot prove exact SIM identity, so deny dialing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return null

        return runCatching {
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
    }

    fun accountMatchesDefault(
        candidateSubscriptionId: Int,
        defaultVoiceSubscriptionId: Int,
    ): Boolean = candidateSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
        candidateSubscriptionId == defaultVoiceSubscriptionId
}
