package pl.michalmatu.aicallbridge

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.ui.AgentAiHubActivity

/** UX-only checks. Do not alter presets, vault fields, grants or calls. */
@RunWith(AndroidJUnit4::class)
class AndroidUx09VisualSemanticsTest {
    private fun walk(view: View, result: MutableList<View>) {
        result += view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            walk(view.getChildAt(index), result)
        }
    }

    @Test fun disabledSkillKeepsInstructionsRetrievableButHiddenByDefault() {
        ActivityScenario.launch(ConversationSkillsActivity::class.java).use { test ->
            test.onActivity { activity ->
                val root = activity.window.decorView
                val values = mutableListOf<View>()
                walk(root, values)
                val inactive = values.filter { (it.tag as? String)?.startsWith("skills-inactive-state:") == true }
                for (label in inactive) {
                    val id = (label.tag as String).substringAfter("skills-inactive-state:")
                    val instructions = requireNotNull(root.findViewWithTag<View>("skills-instructions:" + id))
                    assertEquals("disabled_skill_instruction_visible:" + id, View.GONE, instructions.visibility)
                    assertNotNull(root.findViewWithTag<View>("skills-inactive-toggle:" + id))
                }
            }
        }
    }

    @Test fun usbUsesSinglePageTitleAndFlatRawDetailSection() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { test ->
            test.onActivity { activity ->
                val root = activity.window.decorView
                val values = mutableListOf<View>()
                walk(root, values)
                assertEquals(1, values.filterIsInstance<TextView>().count {
                    it.text.toString() == activity.getString(R.string.usb_ui_title)
                })
                val toggle = requireNotNull(root.findViewWithTag<View>("ui-accordion-toggle:usb-raw"))
                assertTrue(toggle.isClickable)
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                    "ui-accordion-content:usb-raw")).visibility)
                assertNotNull(root.findViewWithTag<View>("usb-status-summary"))
            }
        }
    }

    @Test fun campaignShowsDataFieldsWithExplanationsFolded() {
        ActivityScenario.launch(CampaignToolActivity::class.java).use { test ->
            test.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("campaign-last-result-card"))
                val status = requireNotNull(root.findViewWithTag<TextView>("campaign-execution-summary"))
                assertTrue(status.text.isNotBlank())
                assertFalse(status.text.contains("\n"))
                assertNotNull(root.findViewWithTag<View>("campaign-execution-timestamp"))
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                    "ui-accordion-content:campaign-result-explain")).visibility)
                assertNotNull(root.findViewWithTag<View>("campaign-primary-actions"))
            }
        }
    }

    @Test fun homeKeepsActionsVisibleAndTechnicalDetailsCollapsed() {
        ActivityScenario.launch(MainActivity::class.java).use { test ->
            test.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("app-tab:home"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("home-task-authorization")).isShown)
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                    "ui-accordion-content:home-technical")).visibility)
                assertNotNull(root.findViewWithTag<View>("home-conversation-floating-overlay"))
                assertNotNull(root.findViewWithTag<View>("conversation-floating-button"))
            }
        }
    }

    @Test fun agentSummaryDoesNotReadOutSecretValues() {
        ActivityScenario.launch(AgentAiHubActivity::class.java).use { test ->
            test.onActivity { activity ->
                val root = activity.window.decorView
                for (name in listOf("ConversationSkillsActivity", "ConversationPreparationActivity",
                    "IdentityVaultManagerActivity", "ConversationToolsActivity")) {
                    assertTrue(requireNotNull(root.findViewWithTag<View>(
                        "agent-destination:" + name)).isClickable)
                }
                val all = mutableListOf<View>()
                walk(root, all)
                assertTrue(all.filterIsInstance<TextView>().any {
                    it.text.contains(activity.getString(R.string.ux09_agent_scope_warning))
                })
            }
        }
    }
}
