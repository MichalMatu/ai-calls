package pl.michalmatu.aicallbridge

import android.app.Activity
import android.app.Instrumentation
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.ui.GeneralSettingsActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity
import pl.michalmatu.aicallbridge.ui.AgentAiHubActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/** Navigation only: no CALL, START, STOP, download or preference save is invoked. */
@RunWith(AndroidJUnit4::class)
class AndroidAppBottomNavigationFlowTest {
    private fun openTab(tab: String, destination: Class<out Activity>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(destination.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                home.onActivity { activity ->
                    val view = requireNotNull(activity.window.decorView.findViewWithTag<View>("app-tab:$tab"))
                    assertTrue(view.isShown)
                    view.performClick()
                }
                val next = instrumentation.waitForMonitorWithTimeout(monitor, 12000)
                assertNotNull("tab_destination_missing:$tab", next)
                next?.let {
                    instrumentation.runOnMainSync {
                        assertNotNull(it.window.decorView.findViewWithTag<View>("app-bottom-navigation"))
                        it.finish()
                    }
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun homeTabsNavigateToHistoryAgentAndSettings() {
        openTab("history", ConversationHistoryActivity::class.java)
        openTab("agent", AgentAiHubActivity::class.java)
        openTab("settings", SettingsHubActivity::class.java)
    }

    @Test fun hubOpensConfigurationAndItsBackHeaderCloses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(GeneralSettingsActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(SettingsHubActivity::class.java).use { hub ->
                hub.onActivity { activity ->
                    val entry = requireNotNull(activity.window.decorView
                        .findViewWithTag<View>("settings-destination:GeneralSettingsActivity"))
                    assertTrue(entry.isShown)
                    entry.performClick()
                }
                val target = instrumentation.waitForMonitorWithTimeout(monitor, 12000)
                assertNotNull("general_settings_not_launched", target)
                target?.let {
                    instrumentation.runOnMainSync {
                        val back = requireNotNull(it.window.decorView
                            .findViewWithTag<View>("app-child-back"))
                        assertTrue(back.isShown)
                        back.performClick()
                        assertTrue(it.isFinishing)
                    }
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
