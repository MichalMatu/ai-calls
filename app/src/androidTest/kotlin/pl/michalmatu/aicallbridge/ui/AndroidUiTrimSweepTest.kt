package pl.michalmatu.aicallbridge.ui

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.MainActivity
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity

/** UI-only smoke; no call, role request or profile changes. */
@RunWith(AndroidJUnit4::class)
class AndroidUiTrimSweepTest {
    @Before fun isolatedEmulator() { assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish")) }

    private fun labels(root: View): List<String> = buildList {
        if (root is TextView) add(root.text.toString())
        if (root is ViewGroup) repeat(root.childCount) { addAll(labels(root.getChildAt(it))) }
    }

    @Test fun startHasNoRedundantBrandHeading() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertFalse(labels(root).contains("AI Calls"))
                assertFalse(labels(root).contains(activity.getString(R.string.ui_subtitle)))
                assertNotNull(root.findViewWithTag<View>("home-new-call-card"))
                assertNotNull(root.findViewWithTag<View>("home-stop-ai"))
                assertNotNull(root.findViewWithTag<View>("app-tab:home"))
            }
        }
    }

    @Test fun conversationsHaveNoTopBackToolbar() {
        ActivityScenario.launch(ConversationHistoryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("app-child-toolbar"))
                assertNotNull(root.findViewWithTag<View>("app-tab:history"))
                assertTrue(labels(root).contains(activity.getString(R.string.history_title)))
            }
        }
    }

    @Test fun agentKeepsDestinationsButRemovesScopeFooter() {
        ActivityScenario.launch(AgentAiHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertFalse(labels(root).contains(activity.getString(R.string.ux09_agent_scope_warning)))
                for (name in listOf("ConversationSkillsActivity", "ScenarioLibraryActivity",
                    "ConversationPreparationActivity", "IdentityVaultManagerActivity",
                    "ConversationToolsActivity"))
                    assertNotNull(root.findViewWithTag<View>("agent-destination:$name"))
            }
        }
    }

    @Test fun optionsRemovesIntroAndShowsRoleOnlyInsidePermissions() {
        ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertFalse(labels(root).contains(activity.getString(R.string.ui_settings_hub_title)))
                assertFalse(labels(root).contains(activity.getString(R.string.ui_settings_hub_caption)))
                assertNull(root.findViewWithTag<View>("dialer-role-card"))
                assertNotNull(root.findViewWithTag<View>("settings-destination:GeneralSettingsActivity"))
                assertNotNull(root.findViewWithTag<View>("ui-language-picker"))
                assertTrue(labels(root).contains(activity.getString(R.string.ui_language_hint)))
                assertFalse(labels(root).contains(activity.getString(R.string.ui_settings_hub_group_ai)))
            }
        }
        ActivityScenario.launch(GeneralSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("dialer-role-card"))
                assertNotNull(root.findViewWithTag<View>("dialer-role-status"))
                assertNotNull(root.findViewWithTag<View>("dialer-role-request"))
            }
        }
    }
}
