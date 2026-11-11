package pl.michalmatu.aicallbridge.ui

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.CheckBox
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
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

    @Test fun unsavedToolToggleDoesNotSilentlyCloseOrPersist() {
        ActivityScenario.launch(ConversationToolsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val noteTool = root.findViewWithTag<CheckBox>("tools-setting:SAVE_NOTE")
                assertNotNull("note_toggle_absent", noteTool)
                assertNotNull("end_call_toggle_absent",
                    root.findViewWithTag<CheckBox>("tools-setting:END_CALL"))
                val save = root.findViewWithTag<View>("tools-save-settings")
                assertNotNull(save)
                assertEquals(View.GONE, save.visibility)
                checkNotNull(noteTool).isChecked = !noteTool.isChecked
                assertEquals(View.VISIBLE, save.visibility)
                activity.navigateBack()
                assertFalse("unsaved_changes_closed_without_confirmation", activity.isFinishing)
            }
        }
    }

    @Test fun unsavedToolChoiceSurvivesActivityRecreation() {
        ActivityScenario.launch(ConversationToolsActivity::class.java).use { scenario ->
            var pending = false
            scenario.onActivity { activity ->
                val toggle = checkNotNull(activity.window.decorView
                    .findViewWithTag<CheckBox>("tools-setting:SAVE_NOTE"))
                pending = !toggle.isChecked
                toggle.isChecked = pending
                val save = checkNotNull(activity.window.decorView
                    .findViewWithTag<View>("tools-save-settings"))
                assertEquals(View.VISIBLE, save.visibility)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val toggle = checkNotNull(root.findViewWithTag<CheckBox>("tools-setting:SAVE_NOTE"))
                assertEquals("unsaved_choice_lost_on_rotation", pending, toggle.isChecked)
                assertEquals(View.VISIBLE,
                    checkNotNull(root.findViewWithTag<View>("tools-save-settings")).visibility)
                assertTrue("toggle_accessibility_missing_effect",
                    toggle.contentDescription.toString().length > toggle.text.length)
            }
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
