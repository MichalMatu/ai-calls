package pl.michalmatu.aicallbridge.incoming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import pl.michalmatu.aicallbridge.campaign.AndroidOwnedInCallService
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy

/**
 * Presentation-independent adapter for the existing incoming-call policy.
 * Never answers or places a call; saving updates the existing policy for future sessions.
 */
internal class IncomingAssistantSettingsController(context: Context) {
    private val app = context.applicationContext
    private val store = IncomingAssistantPolicyStore(app)

    enum class SaveResult { NEED_SIM, SAVED_AI, SAVED_MANUAL, FAILED }

    fun current(): Result<IncomingAssistantPolicy> = runCatching { store.load() }

    fun availableAccounts(): List<PhoneAccountHandle> {
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            app.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun displayAccount(account: PhoneAccountHandle): String? = runCatching {
        val original = app.getSystemService(TelecomManager::class.java)
            ?.getPhoneAccount(account)?.label?.toString()?.let(::maskPhoneAccountLabel)
        // Only attach slot/carrier when the Telecom account ID really matches
        // the Android subscription ID. Never guess a SIM from list order.
        val subscription = if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED) runCatching {
            val subscriptionId = account.id.toIntOrNull()
            if (subscriptionId == null) null else
                app.getSystemService(SubscriptionManager::class.java)
                    ?.activeSubscriptionInfoList?.firstOrNull { it.subscriptionId == subscriptionId }
        }.getOrNull() else null
        formatIncomingSimLabel(original, subscription?.carrierName?.toString(),
            subscription?.simSlotIndex?.takeIf { it >= 0 }?.plus(1))
    }.getOrNull()

    /** -1 means unavailable: never silently switch an AI policy to another SIM. */
    fun selectedAccountIndex(accounts: List<PhoneAccountHandle>, accountKey: String?): Int =
        if (accountKey == null) -1 else
            accounts.indexOfFirst { IncomingAssistantPolicyStore.accountKey(it) == accountKey }

    fun save(mode: AnsweringMode, account: PhoneAccountHandle?, recordEvidence: Boolean): SaveResult {
        if (mode == AnsweringMode.AI && account == null) return SaveResult.NEED_SIM
        return runCatching {
            store.save(mode, account?.let(IncomingAssistantPolicyStore::accountKey), recordEvidence)
            AndroidOwnedInCallService.incomingPolicyChanged()
            if (mode == AnsweringMode.AI) SaveResult.SAVED_AI else SaveResult.SAVED_MANUAL
        }.getOrDefault(SaveResult.FAILED)
    }
}

/** Redact account labels in the visible UI only; leave stable account IDs unchanged. */
internal fun maskPhoneAccountLabel(label: String): String =
    Regex("""\+?\d(?:[\d\s.-]*\d)?""").replace(label) { match ->
        val digits = match.value.filter { it.isDigit() }
        if (digits.length < 7) match.value else
            "•".repeat(digits.length - 4) + digits.takeLast(4)
    }

/** Pure display helper: never expose more than the existing masked identifier. */
internal fun formatIncomingSimLabel(maskedLabel: String?, carrier: String?, slot: Int?): String? {
    val label = maskedLabel?.trim().orEmpty()
    val operator = carrier?.trim()?.takeIf { it.isNotEmpty() }
    val simSlot = slot?.takeIf { it > 0 }?.let { "SIM $it" }
    if (operator == null && simSlot == null) return label.ifEmpty { null }
    val maskedNumber = Regex("""•+\d{4}""").find(label)?.value
    val fallback = label.takeIf { it.isNotEmpty() && maskedNumber == null &&
        (operator == null || !it.equals(operator, ignoreCase = true)) &&
        (simSlot == null || !it.equals(simSlot, ignoreCase = true)) &&
        !(operator != null && simSlot != null && it.contains(operator, ignoreCase = true) &&
            it.contains(simSlot, ignoreCase = true)) }
    return listOfNotNull(operator, simSlot, maskedNumber ?: fallback).distinct()
        .joinToString(" · ").ifEmpty { null }
}

/** UI only. This comparison does not grant a call or mutate a saved policy. */
internal data class IncomingSettingsSelection(
    val mode: AnsweringMode,
    val accountKey: String?,
    val recordEvidence: Boolean,
)
