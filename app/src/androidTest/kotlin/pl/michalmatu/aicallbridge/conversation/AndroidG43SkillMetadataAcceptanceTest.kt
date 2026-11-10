package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** G4.3 strictly off-call; metadata remains private, version-pinned and inert. */
@RunWith(AndroidJUnit4::class)
class AndroidG43SkillMetadataAcceptanceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedMetadataRevisionAndRollbackInIsolatedStore() {
        val token = UUID.randomUUID().toString()
        val dir = "g43-metadata-qa-" + token
        val key = "g43-metadata-qa-" + token
        val legacy = "g43-metadata-legacy-" + token
        try {
            fun store() = AndroidConversationPreparationStore(context, dir, key, legacy)
            val baseline = store().loadSkillLibrary()
            val custom = baseline.copyActive("Synthetic G4.3")
                .reviseMetadata("ConversationStyle", SkillMetadata(
                    "Zbieranie wiadomości testowych", "Wyłącznie fikcyjne dane",
                    SkillCapability.MESSAGE_CAPTURE))
            store().saveSkillLibrary(custom)
            val reopened = store().loadSkillLibrary()
            val entry = reopened.snapshot().entries.single { it.id == "ConversationStyle" }
            assertEquals(SkillCapability.MESSAGE_CAPTURE, entry.capability)
            assertEquals("Zbieranie wiadomości testowych", entry.name)
            assertEquals("Wyłącznie fikcyjne dane", entry.description)
            assertEquals(3, entry.version)
            val reverted = reopened.bindVersion("ConversationStyle", 1)
            store().saveSkillLibrary(reverted)
            assertEquals(SkillCapability.GENERAL, store().loadSkillLibrary()
                .snapshot().entries.single { it.id == "ConversationStyle" }.capability)
            assertEquals(1, store().loadSkillLibrary()
                .snapshot().entries.single { it.id == "ConversationStyle" }.version)
            val files = File(context.noBackupFilesDir, dir).listFiles().orEmpty()
            assertTrue(files.isNotEmpty())
            assertFalse(files.any { it.readText().contains("Zbieranie wiadomości testowych") })
            println("G43_METADATA_ENCRYPTED_VERSION_ROLLBACK_PASS")
        } finally {
            File(context.noBackupFilesDir, dir).deleteRecursively()
            context.getSharedPreferences(legacy, 0).edit().clear().commit()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(key)) deleteEntry(key)
            }
        }
    }

    @Test fun skillsAndLabExposeMetadataAndRoutingWithoutChangingOwnerState() {
        val original = AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot()
        ActivityScenario.launch(ConversationSkillsActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("receptionist-tone-options"))
                assertNotNull(root.findViewWithTag<View>("receptionist-personal-edit"))
                assertNull(root.findViewWithTag<View>("skills-history-toggle:ConversationStyle"))
            }
        }
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("lab-skill-routing-diagnostics"))
                assertNotNull(root.findViewWithTag<View>("lab-stop"))
                assertNotNull(root.findViewWithTag<View>("lab-skill-mode"))
            }
        }
        assertEquals(original,
            AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot())
        println("G43_UI_READONLY_RECEPTIONIST_LAB_TRACE_PASS")
    }
}
