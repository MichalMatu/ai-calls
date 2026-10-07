package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.DefaultFactDisclosurePolicy
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.IdentitySecretValue
import pl.michalmatu.aicallbridge.identity.IdentityVaultAead
import pl.michalmatu.aicallbridge.identity.IdentityVaultBlobStorage
import pl.michalmatu.aicallbridge.identity.IdentityVaultCiphertext
import pl.michalmatu.aicallbridge.identity.PersistentIdentityVault
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

class AuthorizedPhoneFactBackendTest {
    private val task = CallTask(
        "Example service",
        "read-only identity disclosure test",
        "identity",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        emptyMap(),
    )
    private val target = CallResolvedTarget("Example service", "+48111111111")
    private val state = TaskGraphStateId("PHONE_REQUEST")

    @Test
    fun `wrong control is rejected before vault access`() {
        val fixture = vaultWithPhone()
        fixture.storage.readCalls = 0
        val result = generate(
            backend(fixture.vault, authorized = true),
            "[[DISCLOSE_AUTHORIZED_FACT:EMAIL]]",
        )

        assertEquals(AuthorizedPhoneFactBackend.ERROR_CONTROL_NOT_SUPPORTED, result.error)
        assertNull(result.text)
        assertEquals(0, fixture.storage.readCalls)
    }

    @Test
    fun `vault presence alone does not authorize disclosure`() {
        val fixture = vaultWithPhone()
        fixture.storage.readCalls = 0
        val result = generate(
            backend(fixture.vault, authorized = false),
            AuthorizedPhoneFactBackend.DISCLOSE_PHONE_CONTROL,
        )

        assertEquals(AuthorizedPhoneFactBackend.ERROR_REQUIRES_USER, result.error)
        assertNull(result.text)
        assertEquals(1, fixture.storage.readCalls)
    }

    @Test
    fun `exact authorized scope resolves phone locally`() {
        val fixture = vaultWithPhone()
        fixture.storage.readCalls = 0
        var disclosureRecorded = false
        val result = generate(
            AuthorizedPhoneFactBackend(
                vault = fixture.vault,
                policy = DefaultFactDisclosurePolicy(),
                scopeProvider = { scope(authorized = true) },
                onDisclosure = { disclosureRecorded = true },
            ),
            AuthorizedPhoneFactBackend.DISCLOSE_PHONE_CONTROL,
        )

        assertNull(result.error)
        assertTrue(disclosureRecorded)
        assertEquals("Numer usługi to 1 2 3 4 5 6 7 8 9.", result.text)
        assertEquals(2, fixture.storage.readCalls)
    }

    @Test
    fun `vault failures return a stable redacted error`() {
        val secretMarker = "DO_NOT_EXPOSE_123456789"
        val fixture = vaultWithPhone()
        fixture.storage.readCalls = 0
        fixture.storage.readFailure = IllegalStateException(secretMarker)

        val result = generate(
            backend(fixture.vault, authorized = true),
            AuthorizedPhoneFactBackend.DISCLOSE_PHONE_CONTROL,
        )

        assertEquals(AuthorizedPhoneFactBackend.ERROR_RESOLUTION_FAILED, result.error)
        assertNull(result.text)
        assertTrue(result.error?.contains(secretMarker) == false)
    }

    private fun backend(
        vault: PersistentIdentityVault,
        authorized: Boolean,
    ) = AuthorizedPhoneFactBackend(
        vault = vault,
        policy = DefaultFactDisclosurePolicy(),
        scopeProvider = { scope(authorized) },
    )

    private fun scope(authorized: Boolean) = AuthorizedPhoneFactScope(
        task = task,
        target = target,
        state = state,
        generation = 7L,
        mode = CallTaskMode.GENUINE,
        authorized = authorized,
    )

    private fun generate(
        backend: AuthorizedPhoneFactBackend,
        input: String,
    ): BackendResult {
        var text: String? = null
        var error: String? = null
        backend.generate(input, object : TextCallAgentBackend.Listener {
            override fun onComplete(value: String) {
                text = value
            }

            override fun onError(reason: String) {
                error = reason
            }
        })
        return BackendResult(text, error)
    }

    private fun vaultWithPhone(): VaultFixture {
        val storage = RecordingStorage()
        val vault = PersistentIdentityVault(storage, PassThroughAead())
        vault.put(IdentityFieldId.PHONE, IdentitySecretValue.of("123456789")).getOrThrow()
        return VaultFixture(vault, storage)
    }

    private data class BackendResult(
        val text: String?,
        val error: String?,
    )

    private data class VaultFixture(
        val vault: PersistentIdentityVault,
        val storage: RecordingStorage,
    )

    private class RecordingStorage : IdentityVaultBlobStorage {
        private var bytes: ByteArray? = null
        var readCalls: Int = 0
        var readFailure: RuntimeException? = null

        override fun read(): ByteArray? {
            readCalls += 1
            readFailure?.let { throw it }
            return bytes?.copyOf()
        }

        override fun write(bytes: ByteArray) {
            this.bytes = bytes.copyOf()
        }

        override fun clear() {
            bytes = null
        }
    }

    private class PassThroughAead : IdentityVaultAead {
        override val algorithmId: String = "TEST_PASSTHROUGH"

        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ) = IdentityVaultCiphertext(
            nonce = byteArrayOf(1),
            ciphertext = plaintext.copyOf(),
        )

        override fun decrypt(
            ciphertext: IdentityVaultCiphertext,
            associatedData: ByteArray,
        ): ByteArray = ciphertext.ciphertextCopy()
    }
}
