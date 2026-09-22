package pl.michalmatu.aicallbridge.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidIdentityVaultContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val aliases = mutableSetOf<String>()
    private val recordNames = mutableSetOf<String>()

    @After
    fun cleanup() {
        val keyStore = androidKeyStore()
        aliases.forEach { alias ->
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
        recordNames.forEach { recordName ->
            AndroidIdentityVaultBlobStorage(context, recordName).clear()
        }
    }

    @Test
    fun ciphertextStorageIsAppPrivateNoBackupAndAtomic() {
        val recordName = newRecordName()
        val storage = AndroidIdentityVaultBlobStorage(context, recordName)
        val first = "ciphertext-v1".toByteArray()
        val second = "ciphertext-v2-complete".toByteArray()

        storage.write(first)
        storage.write(second)

        assertArrayEquals(second, storage.read())
        assertTrue(File(context.noBackupFilesDir, recordName).isFile)
        assertFalse(File(context.filesDir, recordName).exists())
    }

    @Test
    fun keystoreKeyIsNonExportableCreatedOnceAndReusedForAesGcm() {
        val alias = newAlias()
        val aad = "vault-aad".toByteArray()
        val plaintext = "sensitive".toByteArray()
        val first = AndroidKeystoreIdentityVaultAead(alias)

        val ciphertext = first.encrypt(plaintext, aad)
        val storedKey = androidKeyStore().getKey(alias, null)

        assertTrue(storedKey is SecretKey)
        assertNull(storedKey.encoded)
        assertEquals("ANDROID_KEYSTORE_AES_256_GCM_V1", first.algorithmId)
        assertEquals(12, ciphertext.nonceCopy().size)

        val reopened = AndroidKeystoreIdentityVaultAead(alias)
        assertArrayEquals(plaintext, reopened.decrypt(ciphertext, aad))
    }

    @Test
    fun aadAndAlgorithmIdentityAreAuthenticatedAndPlaintextIsNotDurable() {
        val alias = newAlias()
        val recordName = newRecordName()
        val secretText = "jan.identity@example.test"
        val vault = AndroidIdentityVault.create(context, alias, recordName)

        vault.put(IdentityFieldId.EMAIL, IdentitySecretValue.of(secretText)).getOrThrow()

        val durableBytes = File(context.noBackupFilesDir, recordName).readBytes()
        val durableText = durableBytes.toString(StandardCharsets.ISO_8859_1)
        assertFalse(durableText.contains(secretText))
        assertTrue(durableText.contains("ANDROID_KEYSTORE_AES_256_GCM_V1"))
        assertEquals(IdentityVaultBackupPolicy.DEVICE_BOUND_NO_BACKUP, vault.backupPolicy)

        val aead = AndroidKeystoreIdentityVaultAead(alias)
        val ciphertext = aead.encrypt("payload".toByteArray(), "correct-aad".toByteArray())
        assertTrue(
            runCatching {
                aead.decrypt(ciphertext, "wrong-aad".toByteArray())
            }.isFailure,
        )
    }

    @Test
    fun missingKeystoreKeyFailsClosedWithoutReplacingCiphertext() {
        val alias = newAlias()
        val recordName = newRecordName()
        val vault = AndroidIdentityVault.create(context, alias, recordName)
        vault.put(IdentityFieldId.FIRST_NAME, IdentitySecretValue.of("Jan")).getOrThrow()
        val recordFile = File(context.noBackupFilesDir, recordName)
        val originalCiphertext = recordFile.readBytes()

        androidKeyStore().deleteEntry(alias)
        val reopened = AndroidIdentityVault.create(context, alias, recordName)

        assertTrue(reopened.get(IdentityFieldId.FIRST_NAME).isFailure)
        assertTrue(
            reopened.put(IdentityFieldId.LAST_NAME, IdentitySecretValue.of("Kowalski")).isFailure,
        )
        assertArrayEquals(originalCiphertext, recordFile.readBytes())
        assertFalse(androidKeyStore().containsAlias(alias))
    }

    @Test
    fun invalidKeystoreEntryFailsClosedInsteadOfReplacingIt() {
        val alias = newAlias()
        val recordName = newRecordName()
        val vault = AndroidIdentityVault.create(context, alias, recordName)
        vault.put(IdentityFieldId.PHONE, IdentitySecretValue.of("+48123123123")).getOrThrow()
        val recordFile = File(context.noBackupFilesDir, recordName)
        val originalCiphertext = recordFile.readBytes()

        androidKeyStore().deleteEntry(alias)
        createRsaKey(alias)

        val reopened = AndroidIdentityVault.create(context, alias, recordName)
        assertTrue(reopened.get(IdentityFieldId.PHONE).isFailure)
        assertArrayEquals(originalCiphertext, recordFile.readBytes())
        assertTrue(androidKeyStore().getKey(alias, null) !is SecretKey)
    }

    @Test
    fun corruptCiphertextAndUnsupportedRecordsFailClosed() {
        val corruptAlias = newAlias()
        val corruptRecord = newRecordName()
        val vault = AndroidIdentityVault.create(context, corruptAlias, corruptRecord)
        vault.put(IdentityFieldId.PESEL, IdentitySecretValue.of("12345678901")).getOrThrow()
        val corruptFile = File(context.noBackupFilesDir, corruptRecord)
        val corrupted = corruptFile.readBytes()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 0x01).toByte()
        corruptFile.writeBytes(corrupted)

        assertTrue(vault.get(IdentityFieldId.PESEL).isFailure)
        assertTrue(vault.availableFields().isFailure)

        val unsupportedAlias = newAlias()
        val unsupportedRecord = newRecordName()
        val storage = AndroidIdentityVaultBlobStorage(context, unsupportedRecord)
        storage.write(unsupportedEnvelope())
        val unsupportedVault = PersistentIdentityVault(
            storage = storage,
            aead = AndroidKeystoreIdentityVaultAead(unsupportedAlias),
        )

        assertTrue(unsupportedVault.availableFields().isFailure)
        assertFalse(androidKeyStore().containsAlias(unsupportedAlias))
    }

    @Test
    fun ordinaryDiagnosticsDoNotExposePlaintextSecrets() {
        val alias = newAlias()
        val recordName = newRecordName()
        val rawSecret = "DIAGNOSTIC-SECRET-${UUID.randomUUID()}"
        val secret = IdentitySecretValue.of(rawSecret)
        val storage = AndroidIdentityVaultBlobStorage(context, recordName)
        val aead = AndroidKeystoreIdentityVaultAead(alias)
        val vault = PersistentIdentityVault(storage, aead)

        vault.put(IdentityFieldId.ADDRESS, secret).getOrThrow()
        val bytes = requireNotNull(storage.read())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        storage.write(bytes)
        val failure = vault.get(IdentityFieldId.ADDRESS).exceptionOrNull()

        val ordinaryDiagnostics = listOf(
            secret.toString(),
            storage.toString(),
            aead.toString(),
            vault.toString(),
            failure.toString(),
        ).joinToString("\n")
        assertFalse(ordinaryDiagnostics.contains(rawSecret))
        assertFalse(
            requireNotNull(storage.read())
                .toString(StandardCharsets.ISO_8859_1)
                .contains(rawSecret),
        )
    }

    private fun newAlias(): String =
        "aicb-identity-vault-test-${UUID.randomUUID()}".also(aliases::add)

    private fun newRecordName(): String =
        "identity-vault-${UUID.randomUUID()}.bin".also(recordNames::add)

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createRsaKey(alias: String) {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore",
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build(),
        )
        generator.generateKeyPair()
    }

    private fun unsupportedEnvelope(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write("AICBIVLT".toByteArray(StandardCharsets.US_ASCII))
            output.writeInt(99)
        }
        bytes.toByteArray()
    }
}
