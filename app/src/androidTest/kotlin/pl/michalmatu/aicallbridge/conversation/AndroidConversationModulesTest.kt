package pl.michalmatu.aicallbridge.conversation

import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore

@RunWith(AndroidJUnit4::class)
class AndroidConversationModulesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedPreparationSurvivesReopenAndSeparatesDirectionsWithoutTaskAuthority() {
        val suffix = UUID.randomUUID().toString()
        val directory = "conversation-test-$suffix"
        val alias = "conversation-test-$suffix"
        val prefs = "conversation-legacy-test-$suffix"
        try {
            fun store() = AndroidConversationPreparationStore(context, directory, alias, prefs)
            store().save(ConversationPreparation.outgoing().withSettings("synthetic-private-context", setOf(ConversationSkill.CLARIFY), emptySet()))
            store().save(ConversationPreparation.incoming().withSettings("synthetic-incoming-context", emptySet(), emptySet()))
            assertEquals("synthetic-private-context", store().load(ConversationPreparation.OUTGOING_ID).notes)
            assertEquals("synthetic-incoming-context", store().load(ConversationPreparation.INCOMING_ID).notes)
            File(context.noBackupFilesDir, directory).listFiles().orEmpty().forEach {
                assertFalse(it.readText().contains("synthetic-private-context"))
                assertFalse(it.readText().contains("synthetic-incoming-context"))
            }
        } finally { cleanup(directory, alias, prefs) }
    }

    @Test fun legacySkillsMigrateOnceAndDoNotOverwriteAnEditedContext() {
        val suffix = UUID.randomUUID().toString()
        val directory = "conversation-test-$suffix"
        val alias = "conversation-test-$suffix"
        val prefs = "conversation-legacy-test-$suffix"
        val legacy = context.getSharedPreferences(prefs, 0)
        try {
            legacy.edit().putString("grant_id", "old-context-fixture").putStringSet("skills", setOf("CLARIFY")).commit()
            val migrated = AndroidConversationPreparationStore(context, directory, alias, prefs)
            assertEquals(setOf(ConversationSkill.CLARIFY), migrated.load(ConversationPreparation.OUTGOING_ID).skills)
            assertFalse(legacy.contains("skills"))
            migrated.save(ConversationPreparation.outgoing().withSettings("edited-context", emptySet(), emptySet()))
            assertEquals("edited-context", AndroidConversationPreparationStore(context, directory, alias, prefs).load(ConversationPreparation.OUTGOING_ID).notes)
        } finally { cleanup(directory, alias, prefs) }
    }

    @Test fun preparationAndHistoryScreensDoNotChangeTaskAuthorityOrAudioSelection() {
        val grant = CampaignAuthorizationStore(context).load()
        val audio = AudioEngineProfileStore(context).load()
        ActivityScenario.launch(ConversationPreparationActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                assertTrue(labels(activity.window.decorView).any { it ==
                    activity.getString(pl.michalmatu.aicallbridge.R.string.prep_ui_title) })
                assertTrue(labels(activity.window.decorView).any { it ==
                    activity.getString(pl.michalmatu.aicallbridge.R.string.prep_ui_incoming) })
            }
        }
        ActivityScenario.launch(ConversationHistoryActivity::class.java).use { screen ->
            screen.onActivity { activity -> assertTrue(labels(activity.window.decorView).any { it == "Historia rozmów" }) }
        }
        assertEquals(grant, CampaignAuthorizationStore(context).load())
        assertEquals(audio, AudioEngineProfileStore(context).load())
    }

    private fun labels(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) repeat(view.childCount) { addAll(labels(view.getChildAt(it))) }
    }
    private fun cleanup(directory: String, alias: String, prefs: String) {
        File(context.noBackupFilesDir, directory).deleteRecursively()
        context.getSharedPreferences(prefs, 0).edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
