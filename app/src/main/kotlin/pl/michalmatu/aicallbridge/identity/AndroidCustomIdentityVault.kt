package pl.michalmatu.aicallbridge.identity

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Custom owner-supplied facts remain encrypted and OFF the AI disclosure path by default. */
internal enum class CustomVaultKind { TEXT, NUMBER, DATE, SECRET }

internal data class CustomVaultField(
    val id: String,
    val name: String,
    val kind: CustomVaultKind,
    val sensitivity: IdentitySensitivity,
)

/**
 * Separate encrypted addendum, not a replacement or migration of the v1 typed IdentityVault.
 * Never exposes a custom value to a prompt, Call, log, status snapshot, or generic tool.
 */
internal class AndroidCustomIdentityVault(context: Context) {
    private val record = AtomicFile(File(context.applicationContext.noBackupFilesDir,
        "identity-custom-v1.json"))
    private val cipher = AndroidKeystoreIdentityVaultAead(
        "pl.michalmatu.aicallbridge.identity.custom.v1")

    private data class SecretRecord(val field: CustomVaultField, val value: String)

    fun list(): List<CustomVaultField> = synchronized(LOCK) {
        read().map { it.field }
    }

    /** Updating metadata without a new value retains the encrypted existing value. */
    fun upsert(id: String?, name: String, kind: CustomVaultKind,
               sensitivity: IdentitySensitivity, replacement: String?): String = synchronized(LOCK) {
        val normalized = validateName(name)
        val items = read()
        val existing = id?.let { key -> items.singleOrNull { it.field.id == key }
            ?: throw IllegalArgumentException("custom_field_not_found") }
        require(items.none {
            it.field.id != id && it.field.name.equals(normalized, ignoreCase = true)
        }) { "duplicate_custom_name" }
        require(existing != null || items.size < MAX_ITEMS) { "custom_vault_full" }
        val value = (replacement?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.value) ?: throw IllegalArgumentException("missing_custom_value")
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_SECRET_BYTES) {
            "custom_secret_too_long"
        }
        when (kind) {
            CustomVaultKind.NUMBER -> require(value.toBigDecimalOrNull() != null) {
                "invalid_custom_number"
            }
            CustomVaultKind.DATE -> require(runCatching {
                java.time.LocalDate.parse(value)
            }.isSuccess) { "invalid_custom_date" }
            else -> Unit
        }
        val key = existing?.field?.id ?: UUID.randomUUID().toString()
        val updated = SecretRecord(CustomVaultField(key, normalized, kind, sensitivity), value)
        val next = items.filterNot { it.field.id == key } + updated
        write(next)
        key
    }

    fun remove(id: String): Boolean = synchronized(LOCK) {
        val records = read()
        val filtered = records.filterNot { it.field.id == id }
        if (filtered.size == records.size) return@synchronized false
        write(filtered)
        true
    }

    private fun read(): List<SecretRecord> {
        val data = try { record.openRead().use { it.readBytes() } }
            catch (_: java.io.FileNotFoundException) { return emptyList() }
        require(data.size <= MAX_FILE_BYTES) { "custom_vault_record_too_large" }
        val envelope = JSONObject(String(data, StandardCharsets.UTF_8))
        require(envelope.getInt("version") == 1) { "unknown_custom_vault_version" }
        val nonce = Base64.decode(envelope.getString("nonce"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        val plain = cipher.decrypt(IdentityVaultCiphertext(nonce, ciphertext), AAD)
        return try {
            val json = JSONObject(String(plain, StandardCharsets.UTF_8))
            require(json.getInt("version") == 1) { "unknown_custom_payload_version" }
            val array = json.getJSONArray("items")
            require(array.length() <= MAX_ITEMS) { "custom_vault_full" }
            val result = (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                val id = obj.getString("id")
                require(ID_PATTERN.matches(id)) { "custom_field_id_invalid" }
                val label = validateName(obj.getString("name"))
                val value = obj.getString("value")
                require(value.isNotBlank() && value.toByteArray(StandardCharsets.UTF_8).size <= MAX_SECRET_BYTES) {
                    "custom_secret_invalid"
                }
                SecretRecord(CustomVaultField(id, label,
                    CustomVaultKind.valueOf(obj.getString("kind")),
                    IdentitySensitivity.valueOf(obj.getString("sensitivity"))), value)
            }
            require(result.map { it.field.id }.distinct().size == result.size &&
                result.map { it.field.name.lowercase(java.util.Locale.ROOT) }.distinct().size == result.size) {
                "duplicate_custom_field"
            }
            result
        } finally { plain.fill(0) }
    }

    private fun write(items: List<SecretRecord>) {
        if (items.isEmpty()) {
            record.delete()
            return
        }
        val payload = JSONObject().apply {
            put("version", 1)
            put("items", JSONArray().apply {
                for (entry in items) put(JSONObject().apply {
                    put("id", entry.field.id)
                    put("name", entry.field.name)
                    put("kind", entry.field.kind.name)
                    put("sensitivity", entry.field.sensitivity.name)
                    put("value", entry.value)
                })
            })
        }.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            require(payload.size <= MAX_FILE_BYTES) { "custom_payload_too_large" }
            val encrypted = cipher.encrypt(payload, AAD)
            val encoded = JSONObject().apply {
                put("version", 1)
                put("nonce", Base64.encodeToString(encrypted.nonceCopy(), Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(encrypted.ciphertextCopy(), Base64.NO_WRAP))
            }.toString().toByteArray(StandardCharsets.UTF_8)
            require(encoded.size <= MAX_FILE_BYTES) { "custom_vault_record_too_large" }
            val output = record.startWrite()
            try {
                output.write(encoded)
                record.finishWrite(output)
            } catch (e: Throwable) {
                record.failWrite(output)
                throw e
            }
        } finally { payload.fill(0) }
    }

    private fun validateName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotBlank() && normalized.length <= 64 &&
            normalized.none { java.lang.Character.isISOControl(it) }) { "custom_label_invalid" }
        return normalized
    }

    private companion object {
        val LOCK = Any()
        val AAD = "ai-calls-custom-identity-v1".toByteArray(StandardCharsets.UTF_8)
        val ID_PATTERN = Regex("[a-f0-9-]{36}")
        const val MAX_ITEMS = 64
        const val MAX_SECRET_BYTES = 16 * 1024
        const val MAX_FILE_BYTES = 1024 * 1024 + 4096
    }
}
