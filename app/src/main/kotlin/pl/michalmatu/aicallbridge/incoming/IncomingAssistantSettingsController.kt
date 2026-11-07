package pl.michalmatu.aicallbridge.incoming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
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
        app.getSystemService(TelecomManager::class.java)?.getPhoneAccount(account)?.label?.toString()
            ?.let(::maskPhoneAccountLabel)
    }.getOrNull()

    fun selectedAccountIndex(accounts: List<PhoneAccountHandle>, accountKey: String?): Int =
        accounts.indexOfFirst { IncomingAssistantPolicyStore.accountKey(it) == accountKey }.coerceAtLeast(0)

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
    Regex("""\+?\d(?:[\d\s().-]*\d)?""").replace(label) { match ->
        val digits = match.value.count { it.isDigit() }
        if (digits < 7) match.value else {
            var seen = 0
            match.value.map { ch ->
                if (ch.isDigit()) {
                    seen++
                    if (seen <= digits - 4) '•' else ch
                } else ch
            }.joinToString("")
        }
    }
