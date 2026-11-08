package pl.michalmatu.aicallbridge.identity

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Functional persistence/validation acceptance of custom Vault fields against an isolated
 * no-backup test folder. NEVER writes or clears the owner's identity-custom-v1.json.
 * Only synthetic test values; no AI disclosure, call, screenshots or logs of secret values.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCustomVaultFunctionalAcceptanceTest {
    @Test fun createEditReopenValidateAndDeleteSyntheticFieldsWithoutOwnerData() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(app.cacheDir, "custom-vault-qa-" + UUID.randomUUID())
        assertTrue(folder.mkdirs())
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = folder
        }
        try {
            val vault = AndroidCustomIdentityVault(isolated)
            val secret = "TEST-ONLY-SECRET-" + UUID.randomUUID()
            val textId = vault.upsert(null, "QA notes", CustomVaultKind.TEXT,
                IdentitySensitivity.NORMAL, "test notes")
            val secretId = vault.upsert(null, "QA private", CustomVaultKind.SECRET,
                IdentitySensitivity.HIGH, secret)
            val numberId = vault.upsert(null, "QA amount", CustomVaultKind.NUMBER,
                IdentitySensitivity.SENSITIVE, "123.45")
            val dateId = vault.upsert(null, "QA date", CustomVaultKind.DATE,
                IdentitySensitivity.NORMAL, "2026-11-08")
            val reopened = AndroidCustomIdentityVault(isolated)
            val first = reopened.list()
            assertEquals(4, first.size)
            assertEquals(CustomVaultKind.SECRET, first.single { it.id == secretId }.kind)
            assertEquals(IdentitySensitivity.HIGH,
                first.single { it.id == secretId }.sensitivity)

            assertEquals(textId, reopened.upsert(textId, "QA renamed", CustomVaultKind.TEXT,
                IdentitySensitivity.SENSITIVE, null))
            assertEquals("QA renamed", AndroidCustomIdentityVault(isolated)
                .list().single { it.id == textId }.name)

            assertTrue(runCatching {
                reopened.upsert(null, "qa renamed", CustomVaultKind.TEXT,
                    IdentitySensitivity.NORMAL, "duplicate")
            }.isFailure)
            assertTrue(runCatching {
                reopened.upsert(null, "QA invalid number", CustomVaultKind.NUMBER,
                    IdentitySensitivity.NORMAL, "invalid")
            }.isFailure)
            assertTrue(runCatching {
                reopened.upsert(null, "QA invalid date", CustomVaultKind.DATE,
                    IdentitySensitivity.NORMAL, "not-a-date")
            }.isFailure)
            assertEquals(4, reopened.list().size)

            val stored = File(folder, "identity-custom-v1.json")
            assertTrue(stored.isFile)
            val ciphertext = stored.readText()
            assertFalse(ciphertext.contains(secret))
            assertFalse(ciphertext.contains("test notes"))
            assertFalse(ciphertext.contains("123.45"))
            assertFalse(ciphertext.contains("QA renamed"))

            assertTrue(reopened.remove(secretId))
            assertTrue(reopened.remove(textId))
            assertTrue(reopened.remove(numberId))
            assertTrue(reopened.remove(dateId))
            assertTrue(AndroidCustomIdentityVault(isolated).list().isEmpty())
            assertFalse(stored.exists())
            println("CUSTOM_VAULT_SYNTHETIC_CRUD=PASS; owner_data_unmodified=true")
        } finally {
            folder.deleteRecursively()
        }
    }
}
