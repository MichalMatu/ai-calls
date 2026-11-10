package pl.michalmatu.aicallbridge

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.AppointmentMvpActivity
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

    @Test fun factorySkillsShowOnlyFourReadableStatusesWithoutRawInstructions() {
        ActivityScenario.launch(ConversationSkillsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("skills-mode-card"))
                assertNotNull(root.findViewWithTag<View>("skills-behavior-card"))
                for (id in listOf("ConversationStyle", "MessageCapture",
                    "Clarification", "NaturalClosure")) {
                    assertNotNull(root.findViewWithTag<View>("skills-summary:" + id))
                    assertNull(root.findViewWithTag<View>("skills-instructions:" + id))
                }
                assertNull(root.findViewWithTag<View>("skills-secondary-actions"))
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

    @Test fun appointmentUsesExplicitConsentAndVisibleReview() {
        ActivityScenario.launch(AppointmentMvpActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("appointment-authorize-call"))
                assertNotNull(root.findViewWithTag<View>("appointment-review-result"))
                assertNotNull(root.findViewWithTag<View>("appointment-sim-picker"))
                assertNull(root.findViewWithTag<View>("campaign-primary-actions"))
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
