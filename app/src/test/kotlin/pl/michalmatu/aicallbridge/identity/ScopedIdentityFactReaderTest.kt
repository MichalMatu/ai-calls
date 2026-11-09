package pl.michalmatu.aicallbridge.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

class ScopedIdentityFactReaderTest {
    private val task = CallTask(
        "Synthetic clinic", "book appointment", "appointment",
        CallConstraints.unconstrained(), CallPreferences.none(), emptyMap(),
    )
    private val target = CallResolvedTarget("Synthetic clinic", "+48000000000")
    private val state = TaskGraphStateId("IDENTITY_REQUEST")

    @Test fun approvedExactCallReadReturnsOnlyRedactedSecretCapability() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.FIRST_NAME, "SyntheticName")
        val reader = ScopedIdentityFactReader(vault, { true })
        val result = reader.read(request(IdentityFieldId.FIRST_NAME), snapshot(IdentityFieldId.FIRST_NAME))
        assertTrue(result is ScopedIdentityFactRead.Authorized)
        val approved = result as ScopedIdentityFactRead.Authorized
        assertEquals(IdentityFieldId.FIRST_NAME, approved.fieldId)
        assertEquals("SyntheticName", approved.secret.withPlaintext { it })
        assertFalse(result.toString().contains("SyntheticName"))
        assertFalse(approved.secret.toString().contains("SyntheticName"))
    }

    @Test fun wrongTargetStateAndGenerationNeverReadVault() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.EMAIL, "synthetic@example.invalid")
        val reader = ScopedIdentityFactReader(vault, { true })
        storage.readCount = 0
        val scope = snapshot(IdentityFieldId.EMAIL)
        val request = request(IdentityFieldId.EMAIL)
        assertEquals(ScopedIdentityFactRead.Denied,
            reader.read(request.copy(target = CallResolvedTarget("Other", "+48999999999")), scope))
        assertEquals(ScopedIdentityFactRead.Denied,
            reader.read(request.copy(currentState = TaskGraphStateId("WAITING_OFFER")), scope))
        assertEquals(ScopedIdentityFactRead.Denied,
            reader.read(request.copy(snapshotGeneration = 2L), scope))
        assertEquals(0, storage.readCount)
    }

    @Test fun revokedOrThrowingAuthorityNeverReadsVault() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.PHONE, "+48000000000")
        storage.readCount = 0
        assertEquals(ScopedIdentityFactRead.Denied,
            ScopedIdentityFactReader(vault, { false }).read(request(IdentityFieldId.PHONE), snapshot(IdentityFieldId.PHONE)))
        assertEquals(ScopedIdentityFactRead.Denied,
            ScopedIdentityFactReader(vault, { error("revoked") }).read(request(IdentityFieldId.PHONE), snapshot(IdentityFieldId.PHONE)))
        assertEquals(0, storage.readCount)
    }

    @Test fun authorityRevokedDuringDecryptDropsSecretAndDoesNotReturnIt() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.PHONE, "+48000000000")
        var authorized = true
        storage.onRead = { authorized = false }
        val result = ScopedIdentityFactReader(vault, { authorized })
            .read(request(IdentityFieldId.PHONE), snapshot(IdentityFieldId.PHONE))
        assertEquals(ScopedIdentityFactRead.Denied, result)
        assertTrue(storage.readCount > 0)
    }

    @Test fun unapprovedFieldRequestsApprovalWithoutReadingPlaintext() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.EMAIL, "synthetic@example.invalid")
        storage.readCount = 0
        val snapshot = AuthorizedFactSnapshot(task, target, 1L,
            setOf(IdentityFieldId.EMAIL), emptySet())
        val result = ScopedIdentityFactReader(vault, { true })
            .read(request(IdentityFieldId.EMAIL), snapshot)
        assertEquals(ScopedIdentityFactRead.ApprovalRequired, result)
        assertEquals(0, storage.readCount)
    }

    @Test fun staleSnapshotAfterDeletionAndStorageErrorFailClosed() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.FIRST_NAME, "SyntheticName")
        val reader = ScopedIdentityFactReader(vault, { true })
        vault.remove(IdentityFieldId.FIRST_NAME).getOrThrow()
        assertEquals(ScopedIdentityFactRead.Denied,
            reader.read(request(IdentityFieldId.FIRST_NAME), snapshot(IdentityFieldId.FIRST_NAME)))
        storage.failReads = true
        assertEquals(ScopedIdentityFactRead.Denied,
            reader.read(request(IdentityFieldId.FIRST_NAME), snapshot(IdentityFieldId.FIRST_NAME)))
    }

    @Test fun highSensitivityTestModeIsDeniedEvenWhenTaskApproved() {
        val storage = MemoryStorage()
        val vault = vaultWith(storage, IdentityFieldId.PESEL, "00000000000")
        val scope = AuthorizedFactSnapshot(task, target, 1L,
            setOf(IdentityFieldId.PESEL), setOf(IdentityFieldId.PESEL),
            setOf(IdentityFieldId.PESEL), mapOf(IdentityFieldId.PESEL to setOf(state)))
        storage.readCount = 0
        val result = ScopedIdentityFactReader(vault, { true }).read(
            request(IdentityFieldId.PESEL).copy(mode = CallTaskMode.TEST), scope)
        assertEquals(ScopedIdentityFactRead.Denied, result)
        assertEquals(0, storage.readCount)
    }

    private fun request(field: IdentityFieldId) =
        FactDisclosureRequest(task, target, state, field, CallTaskMode.GENUINE, 1L)

    private fun snapshot(field: IdentityFieldId) = AuthorizedFactSnapshot(
        task, target, 1L, setOf(field), setOf(field),
        allowedDisclosureStates = mapOf(field to setOf(state)),
    )

    private fun vaultWith(storage: MemoryStorage, field: IdentityFieldId, value: String):
        PersistentIdentityVault = PersistentIdentityVault(storage, TestOnlyAead()).also {
        it.put(field, IdentitySecretValue.of(value)).getOrThrow()
    }

    private class MemoryStorage : IdentityVaultBlobStorage {
        private var bytes: ByteArray? = null
        var readCount = 0
        var onRead: () -> Unit = {}
        var failReads = false
        override fun read(): ByteArray? {
            readCount++
            onRead()
            if (failReads) error("synthetic_storage_failure")
            return bytes?.copyOf()
        }
        override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun clear() { bytes = null }
    }

    /** Never production crypto; only a deterministic vault codec stub for scoped-read tests. */
    private class TestOnlyAead : IdentityVaultAead {
        override val algorithmId = "TEST_ONLY_CODECHECK"
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) =
            IdentityVaultCiphertext(byteArrayOf(1), plaintext.copyOf())
        override fun decrypt(ciphertext: IdentityVaultCiphertext, associatedData: ByteArray) =
            ciphertext.ciphertextCopy()
    }
}
