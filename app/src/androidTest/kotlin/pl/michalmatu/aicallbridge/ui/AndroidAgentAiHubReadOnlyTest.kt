package pl.michalmatu.aicallbridge.ui

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import pl.michalmatu.aicallbridge.R

/** Emulator only: five Agent destinations, accessible state, async reload, no call or settings writes. */
@RunWith(AndroidJUnit4::class)
class AndroidAgentAiHubReadOnlyTest {
    private val keys = listOf("skills", "scenarios", "profiles", "vault", "tools")

    @Test fun destinationsAreAccessibleAndStatusesResolveAfterRecreation() {
        ActivityScenario.launch(AgentAiHubActivity::class.java).use { scenario ->
            fun assertResolved() {
                var resolved = false
                repeat(45) {
                    scenario.onActivity { activity ->
                        val root = activity.window.decorView
                        assertTrue("screenshot_blocked",
                            (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0)
                        assertNotNull(root.findViewWithTag<View>("app-tab:agent"))
                        assertEquals(5, keys.count { key ->
                            root.findViewWithTag<View>("agent-summary:" + key) != null
                        })
                        val cards = listOf(
                            ConversationSkillsActivityName,
                            ScenarioLibraryActivityName,
                            ConversationPreparationActivityName,
                            IdentityVaultManagerActivityName,
                            ConversationToolsActivityName,
                        )
                        cards.forEach { destination ->
                            assertNotNull("missing_destination:" + destination,
                                root.findViewWithTag<View>("agent-destination:" + destination))
                        }
                        resolved = keys.all { key ->
                            val summary = root.findViewWithTag<TextView>("agent-summary:" + key)
                            val cardName = cards[keys.indexOf(key)]
                            val card = root.findViewWithTag<View>("agent-destination:" + cardName)
                            summary != null &&
                                summary.text.isNotBlank() &&
                                summary.text != activity.getString(R.string.ux07_agent_loading) &&
                                (card?.contentDescription?.contains(summary.text) == true)
                        }
                    }
                    if (resolved) return
                    Thread.sleep(150)
                }
                assertTrue("agent_cards_stuck_loading", resolved)
            }
            assertResolved()
            scenario.recreate()
            assertResolved()
        }
    }

    private companion object {
        const val ConversationSkillsActivityName = "ConversationSkillsActivity"
        const val ScenarioLibraryActivityName = "ScenarioLibraryActivity"
        const val ConversationPreparationActivityName = "ConversationPreparationActivity"
        const val IdentityVaultManagerActivityName = "IdentityVaultManagerActivity"
        const val ConversationToolsActivityName = "ConversationToolsActivity"
    }
}
