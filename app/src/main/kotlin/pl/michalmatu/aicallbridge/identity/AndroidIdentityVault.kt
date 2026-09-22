package pl.michalmatu.aicallbridge.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Production Android wiring for [PersistentIdentityVault].
 *
 * Ciphertext is kept under [Context.getNoBackupFilesDir] and the AEAD key remains inside Android
 * Keystore. This adapter deliberately owns persistence and cryptography only; it has no authority
 * to disclose a stored identity value or to widen the active call/task target.
 */
object AndroidIdentityVault {
    const val DEFAULT_RECORD_FILE_NAME = "identity-vault-v1.bin"
    const val DEFAULT_KEY_ALIAS = "pl.michalmatu.aicallbridge.identity.vault.v1"

    fun create(
        context: Context,
        keyAlias: String = DEFAULT_KEY_ALIAS,
        recordFileName: String = DEFAULT_RECORD_FILE_NAME,
    ): PersistentIdentityVault {
        val appContext = context.applicationContext ?: context
        return PersistentIdentityVault(
            storage = AndroidIdentityVaultBlobStorage(appContext, recordFileName),
            aead = AndroidKeystoreIdentityVaultAead(keyAlias),
        )
    }
}

/** App-private, no-backup ciphertext storage with crash-safe replacement through [AtomicFile]. */
class AndroidIdentityVaultBlobStorage(
    context: Context,
    recordFileName: String = AndroidIdentityVault.DEFAULT_RECORD_FILE_NAME,
) : IdentityVaultBlobStorage {
    private val recordFile: File
    private val atomicFile: AtomicFile

    init {
        requireValidRecordFileName(recordFileName)
        val appContext = context.applicationContext ?: context
        recordFile = File(appContext.noBackupFilesDir, recordFileName)
        atomicFile = AtomicFile(recordFile)
    }

    override fun read(): ByteArray? {
        if (recordFile.exists()) {
            return readExistingRecord()
        }

        // AtomicFile may still recover a previous committed record when the base file is absent.
        return try {
            readExistingRecord()
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun write(bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "identity vault record must not be empty" }
        require(bytes.size <= MAX_RECORD_BYTES) { "identity vault record exceeds storage limit" }

        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    override fun clear() {
        atomicFile.delete()
    }

    private fun readExistingRecord(): ByteArray =
        atomicFile.openRead().use(::readBounded)

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) {
                break
            }
            total += count
            require(total <= MAX_RECORD_BYTES) { "identity vault record exceeds storage limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_RECORD_BYTES = 1024 * 1024 + 4096
        const val MAX_RECORD_FILE_NAME_BYTES = 128

        fun requireValidRecordFileName(recordFileName: String) {
            require(recordFileName.isNotBlank()) { "identity vault record file name must not be blank" }
            require(recordFileName != "." && recordFileName != "..") {
                "identity vault record file name is invalid"
            }
            require(
                recordFileName == File(recordFileName).name &&
                    !recordFileName.contains('/') &&
                    !recordFileName.contains('\\'),
            ) { "identity vault record file name must not contain a path" }
            require(
                recordFileName.toByteArray(StandardCharsets.UTF_8).size <= MAX_RECORD_FILE_NAME_BYTES,
            ) { "identity vault record file name is too long" }
        }
    }
}

/** Android Keystore-backed AES-256/GCM implementation of the host-owned AEAD port. */
class AndroidKeystoreIdentityVaultAead(
    private val keyAlias: String = AndroidIdentityVault.DEFAULT_KEY_ALIAS,
) : IdentityVaultAead {
    override val algorithmId: String = ALGORITHM_ID

    init {
        require(keyAlias.isNotBlank()) { "identity vault key alias must not be blank" }
        require(keyAlias.toByteArray(StandardCharsets.UTF_8).size <= MAX_KEY_ALIAS_BYTES) {
            "identity vault key alias is too long"
        }
    }

    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): IdentityVaultCiphertext {
        require(associatedData.isNotEmpty()) { "identity vault associated data must not be empty" }
        val key = getOrCreateKey()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(associatedData)
            val ciphertext = cipher.doFinal(plaintext)
            val nonce = requireNotNull(cipher.iv) { "identity vault encryption nonce is missing" }
            require(nonce.size == GCM_NONCE_BYTES) { "identity vault encryption nonce size is invalid" }
            IdentityVaultCiphertext(nonce, ciphertext)
        } catch (failure: GeneralSecurityException) {
            throw IllegalStateException("identity vault encryption failed", failure)
        }
    }

    override fun decrypt(
        ciphertext: IdentityVaultCiphertext,
        associatedData: ByteArray,
    ): ByteArray {
        require(associatedData.isNotEmpty()) { "identity vault associated data must not be empty" }
        val key = getExistingKey()
            ?: throw IllegalStateException("identity vault key is unavailable")
        val nonce = ciphertext.nonceCopy()
        require(nonce.size == GCM_NONCE_BYTES) { "identity vault nonce size is invalid" }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext.ciphertextCopy())
        } catch (failure: GeneralSecurityException) {
            throw IllegalStateException("identity vault decryption failed", failure)
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_CREATION_LOCK) {
        val keyStore = openKeyStore()
        getExistingKey(keyStore)?.let { return@synchronized it }

        try {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE_PROVIDER,
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            requireNonExportableAesKey(generator.generateKey())
        } catch (failure: GeneralSecurityException) {
            throw IllegalStateException("identity vault key creation failed", failure)
        }
    }

    private fun getExistingKey(): SecretKey? =
        getExistingKey(openKeyStore())

    private fun getExistingKey(keyStore: KeyStore): SecretKey? {
        val hasAlias = try {
            keyStore.containsAlias(keyAlias)
        } catch (failure: GeneralSecurityException) {
            throw IllegalStateException("identity vault key lookup failed", failure)
        }
        if (!hasAlias) {
            return null
        }

        val key = try {
            keyStore.getKey(keyAlias, null)
        } catch (failure: GeneralSecurityException) {
            throw IllegalStateException("identity vault key lookup failed", failure)
        }
        val secretKey = key as? SecretKey
            ?: throw IllegalStateException("identity vault key entry is invalid")
        return requireNonExportableAesKey(secretKey)
    }

    private fun requireNonExportableAesKey(key: SecretKey): SecretKey {
        require(key.algorithm.equals(KeyProperties.KEY_ALGORITHM_AES, ignoreCase = true)) {
            "identity vault key entry is invalid"
        }
        check(key.encoded == null) { "identity vault key exportability invariant failed" }
        return key
    }

    private fun openKeyStore(): KeyStore = try {
        KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
    } catch (failure: GeneralSecurityException) {
        throw IllegalStateException("identity vault keystore unavailable", failure)
    } catch (failure: java.io.IOException) {
        throw IllegalStateException("identity vault keystore unavailable", failure)
    }

    companion object {
        const val ALGORITHM_ID = "ANDROID_KEYSTORE_AES_256_GCM_V1"

        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_NONCE_BYTES = 12
        private const val MAX_KEY_ALIAS_BYTES = 128
        private val KEY_CREATION_LOCK = Any()
    }
}
