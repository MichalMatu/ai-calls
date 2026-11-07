package pl.michalmatu.aicallbridge

import android.app.Activity
import android.app.Instrumentation
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.ui.GeneralSettingsActivity
import pl.michalmatu.aicallbridge.ui.DeveloperToolsActivity
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantPolicyStore
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity

/** S22 read-only screen/navigation matrix: never presses START, grant, STOP, downloads or Play. */
@RunWith(AndroidJUnit4::class)
class AndroidSettingsNavigationMatrixTest {
    private fun labels(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) repeat(view.childCount) { addAll(labels(view.getChildAt(it))) }
    }

    private fun findButton(view: View, text: String): Button? {
        if (view is Button && view.text.contains(text)) return view
        if (view is ViewGroup) repeat(view.childCount) {
            findButton(view.getChildAt(it), text)?.let { found -> return found }
        }
        return null
    }

    private fun <T : Activity> assertScreen(type: Class<T>, expectedTitle: String) {
        ActivityScenario.launch(type).use { screen ->
            screen.onActivity { activity ->
                assertTrue("screen_title_absent:" + type.simpleName,
                    labels(activity.window.decorView).any { it.contains(expectedTitle) })
            }
        }
    }

    @Test
    fun homeHistoryShortcutNavigatesToProtectedHistoryScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor: Instrumentation.ActivityMonitor = instrumentation.addMonitor(
            ConversationHistoryActivity::class.java.name, null, false
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                home.onActivity { activity ->
                    val button = findButton(activity.window.decorView, "Rozmowy i nagrania")
                    assertNotNull("history_shortcut_absent", button)
                    checkNotNull(button).performClick()
                }
                val history = instrumentation.waitForMonitorWithTimeout(monitor, 12000)
                assertNotNull("history_not_launched_by_shortcut", history)
                try {
                    instrumentation.runOnMainSync {
                        val destination = checkNotNull(history)
                        assertTrue("history_title_absent",
                            labels(destination.window.decorView).any { it.contains(destination.getString(R.string.history_title)) })
                        assertTrue("history_screenshot_blocked",
                            (destination.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0)
                    }
                } finally {
                    if (history != null) instrumentation.runOnMainSync { history.finish() }
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun allConfigurationScreensOpenWithoutChangingCurrentPreferencesOrSkills() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val beforeRuntime = CallRuntimePreferences(context).load()
        val beforeAudio = AudioEngineProfileStore(context).load()
        val beforeIncoming = IncomingAssistantPolicyStore(context).preferences.all.toMap()
        val beforeSkills = AndroidConversationPreparationStore(context).loadSkillLibrary()

        assertScreen(GeneralSettingsActivity::class.java, context.getString(R.string.ui_basic))
        assertScreen(DeveloperToolsActivity::class.java, context.getString(R.string.ui_settings_hub_developer))
        assertScreen(AudioEngineSettingsActivity::class.java, "Ustawienia silników audio AI")
        assertScreen(LanguageModelActivity::class.java, "Modele językowe")
        assertScreen(IncomingAssistantSettingsActivity::class.java, "Odbieranie połączeń")
        assertScreen(ConversationPreparationActivity::class.java, "Przygotowanie rozmowy")
        assertScreen(ConversationSkillsActivity::class.java, "Skills i presety Gemmy")
        assertScreen(ConversationSkillLabActivity::class.java, "Laboratorium skillsów")
        assertScreen(ConversationToolsActivity::class.java, "Narzędzia AI")
        assertScreen(UsbDebugPanelActivity::class.java, "USB / ADB diagnostyka")

        val afterRuntime = CallRuntimePreferences(context).load()
        val afterAudio = AudioEngineProfileStore(context).load()
        val afterIncoming = IncomingAssistantPolicyStore(context).preferences.all.toMap()
        val afterSkills = AndroidConversationPreparationStore(context).loadSkillLibrary()
        assertTrue("runtime_selection_mutated", beforeRuntime == afterRuntime)
        assertTrue("audio_profile_mutated", beforeAudio == afterAudio)
        assertTrue("incoming_policy_mutated", beforeIncoming == afterIncoming)
        assertTrue("skills_active_preset_mutated", beforeSkills.activePresetId == afterSkills.activePresetId)
        assertTrue("skills_presets_mutated", beforeSkills.presets == afterSkills.presets)
        assertTrue("skills_revisions_mutated", beforeSkills.skills == afterSkills.skills)
    }

    @Test
    fun historyListsLocalSessionsOrEmptyStateWithoutExportingAnyRecord() {
        ActivityScenario.launch(ConversationHistoryActivity::class.java).use { screen ->
            var ready = false
            repeat(24) {
                screen.onActivity { activity ->
                    val texts = labels(activity.window.decorView)
                    val title = texts.any { it.contains(activity.getString(R.string.history_title)) }
                    val hasState = texts.any {
                        it.contains(activity.getString(R.string.history_empty)) ||
                            it.startsWith(activity.getString(R.string.history_incoming)) ||
                            it.startsWith(activity.getString(R.string.history_outgoing)) ||
                            it.startsWith(activity.getString(R.string.history_unknown) + " •") ||
                            it.startsWith("Błąd odczytu historii")
                    }
                    ready = title && hasState
                    assertTrue("history_screenshot_blocked",
                        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0)
                }
                if (ready) return@repeat
                Thread.sleep(200)
            }
            assertTrue("history_list_never_resolved", ready)
            screen.onActivity { activity ->
                assertTrue("history_archive_read_failed", labels(activity.window.decorView).none {
                    it.startsWith("Błąd odczytu historii")
                })
            }
        }
    }
}
