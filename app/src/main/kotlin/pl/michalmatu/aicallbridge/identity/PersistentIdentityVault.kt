package pl.michalmatu.aicallbridge.identity

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

enum class IdentityVaultBackupPolicy {
    DEVICE_BOUND_NO_BACKUP,
}

/**
 * Explicit plaintext capability. Ordinary diagnostics are always redacted; callers must enter the
 * [withPlaintext] scope intentionally when a validated disclosure/action owner actually needs it.
 */
class IdentitySecretValue private constructor(
    private val plaintext: String,
) {
    fun <T> withPlaintext(block: (String) -> T): T = block(plaintext)

    override fun toString(): String = "IdentitySecretValue(REDACTED)"

    companion object {
        private const val MAX_SECRET_BYTES = 16 * 1024

        fun of(value: String): IdentitySecretValue {
            require(value.isNotBlank()) { "identity secret must not be blank" }
            require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_SECRET_BYTES) {
                "identity secret exceeds storage limit"
            }
            return IdentitySecretValue(value)
        }
    }
}

/** Opaque durable storage port. Android production code should back this with app-private storage. */
interface IdentityVaultBlobStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    fun clear()
}

/** AEAD result with defensive copies and redacted diagnostics. */
class IdentityVaultCiphertext(
    nonce: ByteArray,
    ciphertext: ByteArray,
) {
    private val nonce: ByteArray = nonce.copyOf()
    private val ciphertext: ByteArray = ciphertext.copyOf()

    init {
        require(this.nonce.isNotEmpty()) { "AEAD nonce must not be empty" }
        require(this.ciphertext.isNotEmpty()) { "AEAD ciphertext must not be empty" }
    }

    fun nonceCopy(): ByteArray = nonce.copyOf()

    fun ciphertextCopy(): ByteArray = ciphertext.copyOf()

    override fun toString(): String =
        "IdentityVaultCiphertext(nonceBytes=${nonce.size}, ciphertextBytes=${ciphertext.size})"
}

/**
 * Cryptographic port. Implementations must provide authenticated encryption and bind
 * [associatedData] on both encryption and decryption.
 */
interface IdentityVaultAead {
    val algorithmId: String

    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): IdentityVaultCiphertext

    fun decrypt(
        ciphertext: IdentityVaultCiphertext,
        associatedData: ByteArray,
    ): ByteArray
}

/**
 * Persistent typed IdentityVault core.
 *
 * Only an encrypted envelope reaches [IdentityVaultBlobStorage]. Plaintext payload bytes are
 * short-lived and zeroed best-effort after crypto/decoding. This class owns no disclosure policy:
 * a value existing here is not permission to use or disclose it.
 */
class PersistentIdentityVault(
    private val storage: IdentityVaultBlobStorage,
    private val aead: IdentityVaultAead,
) {
    val backupPolicy: IdentityVaultBackupPolicy = IdentityVaultBackupPolicy.DEVICE_BOUND_NO_BACKUP

    init {
        require(aead.algorithmId.isNotBlank()) { "AEAD algorithmId must not be blank" }
        require(aead.algorithmId.toByteArray(StandardCharsets.UTF_8).size <= MAX_ALGORITHM_ID_BYTES) {
            "AEAD algorithmId is too long"
        }
    }

    @Synchronized
    fun availableFields(): Result<Set<IdentityFieldId>> = runCatching {
        loadFields().keys.toSet()
    }

    @Synchronized
    fun get(fieldId: IdentityFieldId): Result<IdentitySecretValue?> = runCatching {
        loadFields()[fieldId]?.let(IdentitySecretValue::of)
    }

    @Synchronized
    fun put(
        fieldId: IdentityFieldId,
        value: IdentitySecretValue,
    ): Result<Unit> = runCatching {
        val fields = loadFields().toMutableMap()
        fields[fieldId] = value.withPlaintext { it }
        persistFields(fields)
    }

    @Synchronized
    fun remove(fieldId: IdentityFieldId): Result<Unit> = runCatching {
        val fields = loadFields().toMutableMap()
        fields.remove(fieldId)
        if (fields.isEmpty()) {
            storage.clear()
        } else {
            persistFields(fields)
        }
    }

    @Synchronized
    fun clear(): Result<Unit> = runCatching {
        storage.clear()
    }

    private fun loadFields(): Map<IdentityFieldId, String> {
        val encoded = storage.read() ?: return emptyMap()
        val envelope = IdentityVaultRecordCodec.decodeEnvelope(encoded)
        require(envelope.algorithmId == aead.algorithmId) {
            "identity vault AEAD algorithm mismatch"
        }
        val associatedData = IdentityVaultRecordCodec.associatedData(envelope.algorithmId)
        val plaintext = aead.decrypt(
            IdentityVaultCiphertext(envelope.nonce, envelope.ciphertext),
            associatedData,
        )
        return try {
            IdentityVaultRecordCodec.decodePayload(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun persistFields(fields: Map<IdentityFieldId, String>) {
        val plaintext = IdentityVaultRecordCodec.encodePayload(fields)
        try {
            val associatedData = IdentityVaultRecordCodec.associatedData(aead.algorithmId)
            val encrypted = aead.encrypt(plaintext, associatedData)
            storage.write(
                IdentityVaultRecordCodec.encodeEnvelope(
                    algorithmId = aead.algorithmId,
                    ciphertext = encrypted,
                ),
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private companion object {
        const val MAX_ALGORITHM_ID_BYTES = 128
    }
}

private data class IdentityVaultEncryptedEnvelope(
    val algorithmId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

/** Small deterministic v1 codec; key management remains entirely outside this format. */
private object IdentityVaultRecordCodec {
    private val ENVELOPE_MAGIC = "AICBIVLT".toByteArray(StandardCharsets.US_ASCII)
    private val PAYLOAD_MAGIC = "AICBIDPL".toByteArray(StandardCharsets.US_ASCII)
    private const val ENVELOPE_VERSION = 1
    private const val PAYLOAD_VERSION = 1
    private const val MAX_ALGORITHM_ID_BYTES = 128
    private const val MAX_NONCE_BYTES = 64
    private const val MAX_CIPHERTEXT_BYTES = 1024 * 1024
    private const val MAX_FIELD_ID_BYTES = 128
    private const val MAX_SECRET_BYTES = 16 * 1024

    fun associatedData(algorithmId: String): ByteArray =
        "AICBIVLT|$ENVELOPE_VERSION|$algorithmId".toByteArray(StandardCharsets.UTF_8)

    fun encodeEnvelope(
        algorithmId: String,
        ciphertext: IdentityVaultCiphertext,
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write(ENVELOPE_MAGIC)
            output.writeInt(ENVELOPE_VERSION)
            writeBytes(
                output,
                algorithmId.toByteArray(StandardCharsets.UTF_8),
                MAX_ALGORITHM_ID_BYTES,
            )
            writeBytes(output, ciphertext.nonceCopy(), MAX_NONCE_BYTES)
            writeBytes(output, ciphertext.ciphertextCopy(), MAX_CIPHERTEXT_BYTES)
        }
        bytes.toByteArray()
    }

    fun decodeEnvelope(encoded: ByteArray): IdentityVaultEncryptedEnvelope =
        DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            requireMagic(input, ENVELOPE_MAGIC, "identity vault envelope")
            val version = input.readInt()
            require(version == ENVELOPE_VERSION) { "unsupported identity vault envelope version" }
            val algorithmId = readBytes(input, MAX_ALGORITHM_ID_BYTES)
                .toString(StandardCharsets.UTF_8)
            require(algorithmId.isNotBlank()) { "identity vault algorithm id is blank" }
            val nonce = readBytes(input, MAX_NONCE_BYTES)
            require(nonce.isNotEmpty()) { "identity vault nonce is empty" }
            val ciphertext = readBytes(input, MAX_CIPHERTEXT_BYTES)
            require(ciphertext.isNotEmpty()) { "identity vault ciphertext is empty" }
            require(input.available() == 0) { "trailing identity vault envelope data" }
            IdentityVaultEncryptedEnvelope(algorithmId, nonce, ciphertext)
        }

    fun encodePayload(fields: Map<IdentityFieldId, String>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(PAYLOAD_MAGIC)
                output.writeInt(PAYLOAD_VERSION)
                output.writeInt(fields.size)
                fields.entries.sortedBy { it.key.name }.forEach { (fieldId, value) ->
                    writeBytes(
                        output,
                        fieldId.name.toByteArray(StandardCharsets.UTF_8),
                        MAX_FIELD_ID_BYTES,
                    )
                    val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
                    try {
                        writeBytes(output, valueBytes, MAX_SECRET_BYTES)
                    } finally {
                        valueBytes.fill(0)
                    }
                }
            }
            bytes.toByteArray()
        }

    fun decodePayload(encoded: ByteArray): Map<IdentityFieldId, String> =
        DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            requireMagic(input, PAYLOAD_MAGIC, "identity vault payload")
            val version = input.readInt()
            require(version == PAYLOAD_VERSION) { "unsupported identity vault payload version" }
            val count = input.readInt()
            require(count in 0..IdentityFieldId.entries.size) { "invalid identity field count" }
            val result = linkedMapOf<IdentityFieldId, String>()
            repeat(count) {
                val fieldName = readBytes(input, MAX_FIELD_ID_BYTES)
                    .toString(StandardCharsets.UTF_8)
                val fieldId = runCatching { IdentityFieldId.valueOf(fieldName) }
                    .getOrElse { throw IllegalArgumentException("unknown identity field id", it) }
                require(!result.containsKey(fieldId)) { "duplicate identity field id" }
                val secret = readBytes(input, MAX_SECRET_BYTES).toString(StandardCharsets.UTF_8)
                require(secret.isNotBlank()) { "identity secret must not be blank" }
                result[fieldId] = secret
            }
            require(input.available() == 0) { "trailing identity vault payload data" }
            result.toMap()
        }

    private fun requireMagic(
        input: DataInputStream,
        expected: ByteArray,
        label: String,
    ) {
        val actual = ByteArray(expected.size)
        input.readFully(actual)
        require(actual.contentEquals(expected)) { "$label magic mismatch" }
    }

    private fun writeBytes(
        output: DataOutputStream,
        value: ByteArray,
        maxBytes: Int,
    ) {
        require(value.size <= maxBytes) { "identity vault record field exceeds limit" }
        output.writeInt(value.size)
        output.write(value)
    }

    private fun readBytes(
        input: DataInputStream,
        maxBytes: Int,
    ): ByteArray {
        val size = input.readInt()
        require(size in 0..maxBytes) { "invalid identity vault record field length" }
        return ByteArray(size).also(input::readFully)
    }
}
