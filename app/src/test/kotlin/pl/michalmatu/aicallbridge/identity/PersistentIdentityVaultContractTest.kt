package pl.michalmatu.aicallbridge.identity

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentIdentityVaultContractTest {
    @Test
    fun `secret wrapper redacts ordinary diagnostics and exposes plaintext only in explicit scope`() {
        val raw = "12345678901"
        val secret = IdentitySecretValue.of(raw)

        assertFalse(secret.toString().contains(raw))
        assertEquals("IdentitySecretValue(REDACTED)", secret.toString())
        assertEquals(raw, secret.withPlaintext { it })
    }

    @Test
    fun `typed fields round trip while persistent storage never contains plaintext`() {
        val storage = RecordingStorage()
        val aead = TestAead()
        val vault = PersistentIdentityVault(storage, aead)

        assertTrue(vault.put(IdentityFieldId.FIRST_NAME, IdentitySecretValue.of("Jan")).isSuccess)
        assertTrue(vault.put(IdentityFieldId.EMAIL, IdentitySecretValue.of("jan@example.test")).isSuccess)

        val persisted = requireNotNull(storage.bytes)
        val persistedText = persisted.toString(StandardCharsets.UTF_8)
        assertFalse(persistedText.contains("Jan"))
        assertFalse(persistedText.contains("jan@example.test"))
        assertTrue(aead.encryptAssociatedData.isNotEmpty())
        assertEquals(IdentityVaultBackupPolicy.DEVICE_BOUND_NO_BACKUP, vault.backupPolicy)

        val reopened = PersistentIdentityVault(storage, aead)
        assertEquals(
            setOf(IdentityFieldId.FIRST_NAME, IdentityFieldId.EMAIL),
            reopened.availableFields().getOrThrow(),
        )
        assertEquals(
            "Jan",
            reopened.get(IdentityFieldId.FIRST_NAME).getOrThrow()!!.withPlaintext { it },
        )
        assertEquals(
            "jan@example.test",
            reopened.get(IdentityFieldId.EMAIL).getOrThrow()!!.withPlaintext { it },
        )
    }

    @Test
    fun `remove rewrites encrypted state and clear deletes the durable record`() {
        val storage = RecordingStorage()
        val vault = PersistentIdentityVault(storage, TestAead())
        vault.put(IdentityFieldId.FIRST_NAME, IdentitySecretValue.of("Jan")).getOrThrow()
        vault.put(IdentityFieldId.PHONE, IdentitySecretValue.of("+48123123123")).getOrThrow()

        assertTrue(vault.remove(IdentityFieldId.FIRST_NAME).isSuccess)
        assertNull(vault.get(IdentityFieldId.FIRST_NAME).getOrThrow())
        assertEquals(setOf(IdentityFieldId.PHONE), vault.availableFields().getOrThrow())
        assertFalse(requireNotNull(storage.bytes).toString(StandardCharsets.UTF_8).contains("+48123123123"))

        assertTrue(vault.clear().isSuccess)
        assertNull(storage.bytes)
        assertTrue(vault.availableFields().getOrThrow().isEmpty())
    }

    @Test
    fun `ciphertext corruption fails closed instead of returning partial or fallback plaintext`() {
        val storage = RecordingStorage()
        val vault = PersistentIdentityVault(storage, TestAead())
        vault.put(IdentityFieldId.PESEL, IdentitySecretValue.of("12345678901")).getOrThrow()

        val corrupted = requireNotNull(storage.bytes).copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 0x01).toByte()
        storage.bytes = corrupted

        assertTrue(vault.get(IdentityFieldId.PESEL).isFailure)
        assertTrue(vault.availableFields().isFailure)
    }

    @Test
    fun `unsupported encrypted record version fails before decryption`() {
        val storage = RecordingStorage().also { it.bytes = unsupportedEnvelope() }
        val aead = TestAead()
        val vault = PersistentIdentityVault(storage, aead)

        assertTrue(vault.availableFields().isFailure)
        assertEquals(0, aead.decryptCalls)
    }

    @Test
    fun `associated data mismatch fails closed`() {
        val storage = RecordingStorage()
        val writerAead = TestAead()
        val vault = PersistentIdentityVault(storage, writerAead)
        vault.put(IdentityFieldId.LAST_NAME, IdentitySecretValue.of("Kowalski")).getOrThrow()

        val wrongContextAead = TestAead(associatedDataOverride = "wrong-context".toByteArray())
        val reopened = PersistentIdentityVault(storage, wrongContextAead)

        assertTrue(reopened.get(IdentityFieldId.LAST_NAME).isFailure)
    }

    private class RecordingStorage : IdentityVaultBlobStorage {
        var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(bytes: ByteArray) {
            this.bytes = bytes.copyOf()
        }

        override fun clear() {
            bytes = null
        }
    }

    private class TestAead(
        private val associatedDataOverride: ByteArray? = null,
    ) : IdentityVaultAead {
        override val algorithmId: String = "TEST_AES_GCM"
        private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        private val random = SecureRandom()
        var encryptAssociatedData: ByteArray = byteArrayOf()
            private set
        var decryptCalls: Int = 0
            private set

        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): IdentityVaultCiphertext {
            encryptAssociatedData = associatedData.copyOf()
            val nonce = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD(associatedDataOverride ?: associatedData)
            return IdentityVaultCiphertext(nonce, cipher.doFinal(plaintext))
        }

        override fun decrypt(
            ciphertext: IdentityVaultCiphertext,
            associatedData: ByteArray,
        ): ByteArray {
            decryptCalls += 1
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, ciphertext.nonceCopy()),
            )
            cipher.updateAAD(associatedDataOverride ?: associatedData)
            return cipher.doFinal(ciphertext.ciphertextCopy())
        }
    }

    private fun unsupportedEnvelope(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("AICBIVLT".toByteArray(StandardCharsets.US_ASCII))
            data.writeInt(99)
            writeBytes(data, "TEST_AES_GCM".toByteArray(StandardCharsets.UTF_8))
            writeBytes(data, ByteArray(12))
            writeBytes(data, byteArrayOf(1, 2, 3))
        }
        return output.toByteArray()
    }

    private fun writeBytes(output: DataOutputStream, value: ByteArray) {
        output.writeInt(value.size)
        output.write(value)
    }
}
