package pl.michalmatu.aicallbridge

import android.app.Activity
import android.app.Instrumentation
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsAdvancedActivity
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

    private fun hasHistoryRow(view: View): Boolean =
        (view.tag as? String)?.startsWith("history-open:") == true ||
            (view is ViewGroup && (0 until view.childCount).any {
                hasHistoryRow(view.getChildAt(it))
            })

    private fun <T : Activity> assertScreen(type: Class<T>, expectedTitle: String? = null,
                                            expectedResource: Int = 0) {
        ActivityScenario.launch(type).use { screen ->
            screen.onActivity { activity ->
                // Compare with the Activity locale, not the instrumentation/system locale.
                val localizedTitle = if (expectedResource != 0) {
                    activity.getString(expectedResource)
                } else requireNotNull(expectedTitle)
                assertTrue("screen_title_absent:" + type.simpleName,
                    labels(activity.window.decorView).any { it.contains(localizedTitle) })
            }
        }
    }

    @Test
    fun homeHistoryTabNavigatesToProtectedHistoryScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor: Instrumentation.ActivityMonitor = instrumentation.addMonitor(
            ConversationHistoryActivity::class.java.name, null, false
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                home.onActivity { activity ->
                    val tab = activity.window.decorView.findViewWithTag<View>("app-tab:history")
                    assertNotNull("history_tab_absent", tab)
                    checkNotNull(tab).performClick()
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

        assertScreen(GeneralSettingsActivity::class.java, expectedResource = R.string.ui_basic)
        assertScreen(DeveloperToolsActivity::class.java, expectedResource = R.string.ui_settings_hub_developer)
        assertScreen(AudioEngineSettingsActivity::class.java, expectedResource = R.string.eng_ui_title)
        assertScreen(LanguageModelActivity::class.java, expectedResource = R.string.models_ui_title)
        assertScreen(IncomingAssistantSettingsActivity::class.java, expectedResource = R.string.incoming_ui_title)
        assertScreen(ConversationPreparationActivity::class.java, expectedResource = R.string.prep_ui_title)
        assertScreen(ConversationSkillsActivity::class.java, expectedResource = R.string.skills_ui_title)
        assertScreen(ConversationSkillsAdvancedActivity::class.java, expectedResource = R.string.skills_advanced_title)
        assertScreen(ConversationSkillLabActivity::class.java, expectedResource = R.string.lab_ui_title)
        assertScreen(ConversationToolsActivity::class.java, expectedResource = R.string.tools_ui_title)
        assertScreen(UsbDebugPanelActivity::class.java, expectedResource = R.string.usb_ui_title)

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
                            it.startsWith(activity.getString(R.string.history_read_error))
                    } || hasHistoryRow(activity.window.decorView)
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
                    it.startsWith(activity.getString(R.string.history_read_error))
                })
            }
        }
    }
}
