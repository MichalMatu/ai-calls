package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.incoming.IncomingSecretaryScenario
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Strictly off-call; no Telecom, audio, user data or executed model tools. */
@RunWith(AndroidJUnit4::class)
class AndroidSkillPresetMatrixTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aEncryptedEditVersionSwitchAndRollbackInIsolatedStore() {
        val unique = UUID.randomUUID().toString()
        val dir = "skill-qa-" + unique
        val key = "skill-qa-" + unique
        val legacy = "skill-qa-legacy-" + unique
        try {
            fun store() = AndroidConversationPreparationStore(context, dir, key, legacy)
            val factory = store().loadSkillLibrary()
            assertEquals(2, factory.presets.size)
            val custom = factory.copyActive("Synthetic QA")
                .reviseSkill("Clarification", "Synthetic-only QA instruction.")
            store().saveSkillLibrary(custom)
            val read = store().loadSkillLibrary()
            assertEquals(2, read.snapshot().entries.single { it.id == "Clarification" }.version)
            assertEquals(1, read.snapshot("natural").entries.single { it.id == "Clarification" }.version)
            store().saveSkillLibrary(read.bindVersion("NaturalClosure", null))
            assertFalse(store().loadSkillLibrary().snapshot().entries.any { it.id == "NaturalClosure" })
            store().saveSkillLibrary(store().loadSkillLibrary()
                .bindVersion("Clarification", 1).bindVersion("NaturalClosure", 1))
            val restored = store().loadSkillLibrary()
            assertEquals(4, restored.snapshot().entries.size)
            assertEquals(1, restored.snapshot().entries.single { it.id == "Clarification" }.version)
            assertEquals(2, restored.skills.single { it.id == "Clarification" }.revisions.size)
            val encrypted = File(context.noBackupFilesDir, dir).listFiles().orEmpty()
            assertTrue(encrypted.isNotEmpty())
            assertFalse(encrypted.any { it.readText().contains("Synthetic-only QA instruction.") })
            println("SKILL_QA_ENCRYPTION_VERSION_ROLLBACK=PASS")
        } finally {
            File(context.noBackupFilesDir, dir).deleteRecursively()
            context.getSharedPreferences(legacy, 0).edit().clear().commit()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(key)) deleteEntry(key)
            }
        }
    }

    @Test fun bSkillsAndLaboratoryScreensLaunchWithoutMutatingActivePreset() {
        val original = AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot()
        // Stable semantic view tags are locale-independent. Do not assert literal PL
        // copy: language and UX text may change without breaking Skills navigation.
        ActivityScenario.launch(ConversationSkillsActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                val tone = requireNotNull(root.findViewWithTag<android.widget.RadioGroup>(
                    "receptionist-tone-options"))
                assertEquals(2, tone.childCount)
                assertNotNull(root.findViewWithTag<View>("receptionist-personal-edit"))
                assertNull(root.findViewWithTag<View>("skills-behavior-card"))
                assertNull(root.findViewWithTag<View>("skills-preset-picker"))
            }
        }
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(requireNotNull(root.findViewWithTag<View>("lab-preset-picker")).isShown)
                assertNotNull(root.findViewWithTag<View>("lab-case-picker"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("lab-start")).isClickable)
                assertTrue(requireNotNull(root.findViewWithTag<View>("lab-adaptive-start")).isClickable)
                assertNotNull(root.findViewWithTag<View>("lab-manual-composer"))
                assertNotNull(root.findViewWithTag<View>("lab-manual-send"))
                assertNotNull(root.findViewWithTag<View>("lab-stop"))
                assertNotNull(root.findViewWithTag<View>("lab-turn-timeline"))
            }
        }
        assertEquals(original, AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot())
        println("SKILL_QA_UI=PASS_READONLY_LOCALE_INDEPENDENT")
    }
}
