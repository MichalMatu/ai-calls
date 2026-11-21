package pl.michalmatu.aicallbridge

import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueGenerationSettings
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationPreparation
import pl.michalmatu.aicallbridge.conversation.ConversationPromptSettingsActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

@RunWith(AndroidJUnit4::class)
class AndroidDialogueSettingsAcceptanceTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    @Before fun emulatorOnly() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }

    private fun editDialog(fieldTag: String, edit: (View) -> Unit) {
        instrument.waitForIdleSync()
        instrument.runOnMainSync {
            val dialog = WindowInspector.getGlobalWindowViews().last {
                it.findViewWithTag<EditText>(fieldTag) != null
            }
            edit(dialog)
            assertTrue(dialog.findViewById<Button>(android.R.id.button1).performClick())
        }
        instrument.waitForIdleSync()
    }

    @Test fun incomingRoleIsEditableFromSettingsAndLoadsIntoTheCallProfile() {
        val store = AndroidConversationPreparationStore(instrument.targetContext)
        val original = store.loadPromptSettings()
        val monitor = instrument.addMonitor(ConversationPromptSettingsActivity::class.java.name, null, false)
        val syntheticRole = "Ustal sprawę testowego rozmówcy i podsumuj ją w jednym zdaniu."
        try {
            ActivityScenario.launch(SettingsHubActivity::class.java).use { hub ->
                hub.onActivity { settings ->
                    val entry = requireNotNull(settings.window.decorView.findViewWithTag<View>(
                        "settings-destination:ConversationPromptSettingsActivity"))
                    assertTrue(entry.performClick())
                }
                val settings = requireNotNull(instrument.waitForMonitorWithTimeout(monitor, 15_000))
                try {
                    instrument.runOnMainSync {
                        assertTrue(settings.window.decorView.findViewWithTag<View>("prompt_settings_incoming").performClick())
                    }
                    editDialog("conversation-prompt-field") {
                        it.findViewWithTag<EditText>("conversation-prompt-field").setText(syntheticRole)
                    }
                    val restored = store.load(ConversationPreparation.INCOMING_ID).promptSettings
                    assertEquals(original.copy(incomingRole = syntheticRole), restored)
                } finally {
                    instrument.runOnMainSync { settings.finish() }
                }
            }
        } finally {
            instrument.removeMonitor(monitor)
            store.updatePromptSettings { original }
        }
    }

    @Test fun nativeGenerationSettingsAreSavedFromModelScreen() {
        val store = AudioEngineProfileStore(instrument.targetContext)
        val original = requireNotNull(store.load())
        try {
            ActivityScenario.launch(LanguageModelActivity::class.java).use { screen ->
                screen.onActivity {
                    assertTrue(it.window.decorView.findViewWithTag<View>("model-generation-settings").performClick())
                }
                editDialog("model_generation_context") { dialog ->
                    for ((field, value) in mapOf("context" to "6144", "output" to "768", "temperature" to "0.4",
                        "top_k" to "16", "top_p" to "0.9")) {
                        dialog.findViewWithTag<EditText>("model_generation_$field").setText(value)
                    }
                }
                assertEquals(original.copy(generation = DialogueGenerationSettings(6144, 768, 0.4, 16, 0.9)),
                    store.load())
            }
        } finally {
            assertTrue(store.save(original))
        }
    }
}
