package pl.michalmatu.aicallbridge.incoming

import android.content.Context
import android.content.SharedPreferences
import android.telecom.PhoneAccountHandle
import java.io.IOException
import java.util.UUID
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy

/** Accepted receiving policy, separate from prompts and from outgoing CLIR authorization. */
internal class IncomingAssistantPolicyStore(internal val preferences: SharedPreferences) {
    constructor(context: Context, name: String = PREFERENCES) : this(context.getSharedPreferences(name,Context.MODE_PRIVATE))
    fun load(): IncomingAssistantPolicy {
        if (!preferences.contains("mode")) return IncomingAssistantPolicy("manual", AnsweringMode.MANUAL, null, true)
        return IncomingAssistantPolicy(
            checkNotNull(preferences.getString("id", null)),
            AnsweringMode.valueOf(checkNotNull(preferences.getString("mode", null))),
            preferences.getString("account", null),
            preferences.getBoolean("record", true),
        )
    }
    fun save(mode: AnsweringMode, accountKey: String?, record: Boolean): IncomingAssistantPolicy {
        val policy = IncomingAssistantPolicy(UUID.randomUUID().toString(), mode, accountKey, record)
        if (!preferences.edit().putString("id",policy.id).putString("mode",mode.name)
            .putString("account",accountKey).putBoolean("record",record).commit()) {
            throw IOException("incoming_policy_save_failed")
        }
        return policy
    }
    /** Revocation does not require a decodable old grant/context. */
    fun disable() {
        if (!preferences.contains("mode") ||
            runCatching { preferences.getString("mode", null) }.getOrNull() == AnsweringMode.MANUAL.name) return
        val account=runCatching { preferences.getString("account",null) }.getOrNull()
        val record=runCatching { preferences.getBoolean("record",true) }.getOrDefault(true)
        save(AnsweringMode.MANUAL,account,record)
    }
    companion object {
        const val PREFERENCES = "incoming_assistant_policy_v1"
        fun accountKey(handle: PhoneAccountHandle): String = handle.componentName.flattenToString() + "/" + handle.id
    }
}
