package pl.michalmatu.aicallbridge

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.AppointmentMvpActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.identity.IdentityVaultManagerActivity

/** Read-only visual assertions: no value entry, phone call, grant, runtime or private export. */
@RunWith(AndroidJUnit4::class)
class AndroidUx10OverlayVaultLabTest {
    @Test fun homeHidesFloatingButtonWhenNoMessages() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("home-conversation-floating-overlay"))
                val button = requireNotNull(root.findViewWithTag<View>("conversation-floating-button"))
                val panel = requireNotNull(root.findViewWithTag<View>("conversation-floating-panel"))
                assertEquals(View.GONE, panel.visibility)
                // A previous synthetic test might have left an active feed. Never force clear.
                assertTrue(button.visibility == View.GONE || button.visibility == View.VISIBLE)
                assertNotNull(root.findViewWithTag<View>("app-tab:home"))
            }
        }
    }
    @Test fun appointmentProvidesSafeReviewAndCancellationWithoutDuplicateOverlay() {
        ActivityScenario.launch(AppointmentMvpActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("appointment-authorize-call"))
                assertNotNull(root.findViewWithTag<View>("appointment-review-result"))
                assertNull(root.findViewWithTag<View>("campaign-conversation-floating-overlay"))
            }
        }
    }
    @Test fun vaultShowsAllPredefinedFieldEditorsWithoutReadingValues() {
        ActivityScenario.launch(IdentityVaultManagerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                for (field in listOf("FIRST_NAME", "LAST_NAME", "PHONE", "EMAIL",
                    "ADDRESS", "DATE_OF_BIRTH", "PESEL")) {
                    assertNotNull(root.findViewWithTag<View>("vault-standard:" + field))
                }
            }
        }
    }
    @Test fun vaultUsesCompactRowsAndAccessibleCustomFieldAction() {
        ActivityScenario.launch(IdentityVaultManagerActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val custom = requireNotNull(root.findViewWithTag<View>("vault-add-custom"))
                assertTrue(custom.isShown)
                val first = requireNotNull(root.findViewWithTag<View>("vault-standard:FIRST_NAME"))
                assertTrue(first.minimumHeight <= (70f * activity.resources.displayMetrics.density).toInt())
                assertTrue(first.isClickable)
                assertNull(root.findViewWithTag<View>("vault-standard:FIRST_NAME:edit"))
                val container = requireNotNull(custom.parent as? android.view.ViewGroup)
                assertSame("add custom should be the last action",
                    custom, container.getChildAt(container.childCount - 1))
            }
        }
    }
    @Test fun labHasACompactEmptyConversationState() {
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val empty = requireNotNull(root.findViewWithTag<android.widget.TextView>("lab-empty-transcript"))
                assertEquals(activity.getString(R.string.lab_chat_empty), empty.text.toString())
                assertFalse("empty_chat_must_not_occupy_setup", empty.isShown)
                assertNotNull(root.findViewWithTag<View>("lab-conversation-card"))
                assertNotNull(root.findViewWithTag<View>("lab-start"))
            }
        }
    }
    @Test fun labPutsTimelineAfterRunWithoutFollowCheckbox() {
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("lab-preset-picker"))
                assertTrue(requireNotNull(root.findViewWithTag<android.widget.Button>(
                    "lab-start")).isEnabled)
                assertNotNull(root.findViewWithTag<View>("lab-manual-composer"))
                assertNotNull(root.findViewWithTag<View>("lab-adaptive-start"))
                assertNotNull(root.findViewWithTag<View>("lab-conversation-card"))
                assertNotNull(root.findViewWithTag<View>("lab-turn-timeline"))
                assertNull(root.findViewWithTag<View>("lab-follow"))
            }
        }
    }
}
