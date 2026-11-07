package pl.michalmatu.aicallbridge.conversation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversationcore.ScenarioCatalog
import pl.michalmatu.aicallbridge.conversationcore.ScenarioPersonalLibrary

/** Synthetic personal scenario records only; no production data, phone, grant or CALL. */
@RunWith(AndroidJUnit4::class)
class AndroidPersonalScenarioStoreContractTest {
    @Test fun personalScenariosUseExistingEncryptedStoreAndPreserveTemplate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nonce = UUID.randomUUID().toString()
        val directory = "scenario-test-$nonce"
        val keyAlias = "pl.michalmatu.aicallbridge.scenario.test.$nonce"
        val testDir = File(context.noBackupFilesDir, directory)
        try {
            val store = AndroidConversationPreparationStore(context,
                directoryName = directory,
                keyAlias = keyAlias,
                legacyPreferencesName = "scenario_legacy_unused_$nonce",
            )
            val template = checkNotNull(ScenarioCatalog.byId("mechanic"))
            assertTrue(store.loadPersonalScenarios().items.isEmpty())
            val initial = template.draft()
            val created = ScenarioPersonalLibrary().create(template, initial,
                id = "personal-test", title = "Mój mechanik", goal = "Ustal termin")
            store.savePersonalScenarios(created)
            assertEquals("300 zł", store.loadPersonalScenarios()
                .items.single().current.values["budget"])
            val revised = store.loadPersonalScenarios().revise(
                "personal-test", "Mój mechanik", "Ustal termin",
                initial.withValue("budget", "400 zł", template))
            store.savePersonalScenarios(revised)
            assertEquals(2, store.loadPersonalScenarios().items.single().current.number)
            assertEquals("400 zł", store.loadPersonalScenarios()
                .items.single().current.values["budget"])
            assertNull("personal copies never overwrite the factory form",
                store.loadScenarioDraft("mechanic"))
            val encrypted = testDir.listFiles().orEmpty().filter { it.isFile }
            assertTrue(encrypted.isNotEmpty())
            encrypted.forEach {
                val body = it.readText(Charsets.UTF_8)
                assertFalse("AES-GCM storage must not expose scenario values", body.contains("Mój mechanik"))
                assertFalse(body.contains("400 zł"))
            }
        } finally {
            testDir.deleteRecursively()
            runCatching {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(keyAlias)
            }
        }
    }
}
