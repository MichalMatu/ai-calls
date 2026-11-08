package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
            assertEquals(3, factory.presets.size)
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
        ActivityScenario.launch(ConversationSkillsActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val text = labels(activity.window.decorView).joinToString("|")
                assertTrue(text.contains("Skills i presety Gemmy"))
                assertTrue(text.contains("Utwórz własną kopię"))
                assertTrue(text.contains("Laboratorium skillsów"))
            }
        }
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val text = labels(activity.window.decorView).joinToString("|")
                assertTrue(text.contains("Laboratorium skillsów"))
                assertTrue(text.contains("Uruchom testy wieloturowe"))
                assertTrue(text.contains("Ocena naturalności"))
            }
        }
        assertEquals(original, AndroidConversationPreparationStore(context).loadSkillLibrary().snapshot())
        println("SKILL_QA_UI=PASS_READONLY")
    }

    private fun labels(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) repeat(view.childCount) { addAll(labels(view.getChildAt(it))) }
    }
}
