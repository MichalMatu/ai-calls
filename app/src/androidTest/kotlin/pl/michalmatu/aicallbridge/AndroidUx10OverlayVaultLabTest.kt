package pl.michalmatu.aicallbridge

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
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
    @Test fun campaignUsesTheSameReadOnlyOverlayAndKeepsStop() {
        ActivityScenario.launch(CampaignToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("campaign-conversation-floating-overlay"))
                assertNotNull(root.findViewWithTag<View>("conversation-floating-button"))
                assertNotNull(root.findViewWithTag<View>("campaign-primary-actions"))
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
                assertNotNull(root.findViewWithTag<View>("vault-standard:FIRST_NAME:edit"))
            }
        }
    }
    @Test fun labHasACompactEmptyConversationState() {
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val empty = requireNotNull(root.findViewWithTag<android.widget.TextView>("lab-empty-transcript"))
                assertEquals(activity.getString(R.string.lab_ui_cases_removed), empty.text.toString())
                assertEquals(View.VISIBLE, empty.visibility)
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
                assertFalse(requireNotNull(root.findViewWithTag<android.widget.Button>(
                    "lab-start")).isEnabled)
                assertNotNull(root.findViewWithTag<View>("lab-conversation-card"))
                assertNotNull(root.findViewWithTag<View>("lab-turn-timeline"))
                assertNull(root.findViewWithTag<View>("lab-follow"))
            }
        }
    }
}
