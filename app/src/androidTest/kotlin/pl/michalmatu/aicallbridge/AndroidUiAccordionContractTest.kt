package pl.michalmatu.aicallbridge

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore

/** Read-only UI checks. Neither case clicks authorization, dial, STOP, or permissions. */
@RunWith(AndroidJUnit4::class)
class AndroidUiAccordionContractTest {
    @Test
    fun homePreservesSafeActionsWithRealNavigationInsteadOfEmptyAccordions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val before = CallRuntimePreferences(activity).load()
                val skillsBefore = AndroidConversationPreparationStore(activity).loadSkillLibrary()
                assertTrue(findButton(root, activity.getString(R.string.home_ui_start))!!.isShown)
                assertTrue(findButton(root, activity.getString(R.string.ui_campaigns))!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertNull(findButton(root, "Rozmowy i nagrania"))
                assertNull(findButton(root, "Laboratorium offline"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:history")).isShown)
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:lab")).isShown)
                assertTrue(findButton(root, activity.getString(R.string.home_ui_one_tap))!!.isShown)
                assertNotNull(root.findViewWithTag<View>("app-tab:settings"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("home-action-feedback")).isShown)
                assertTrue(requireNotNull(root.findViewWithTag<View>("home-live-runtime")).isShown)
                assertNull(root.findViewWithTag<View>("ui-accordion-content:basic"))
                assertNull(root.findViewWithTag<View>("ui-accordion-content:advanced"))
                val after = CallRuntimePreferences(activity).load()
                val skillsAfter = AndroidConversationPreparationStore(activity).loadSkillLibrary()
                assertEquals(before, after)
                assertEquals(skillsBefore.activePresetId, skillsAfter.activePresetId)
                assertEquals(skillsBefore.presets, skillsAfter.presets)
                assertEquals(skillsBefore.skills, skillsAfter.skills)
            }
        }
    }

    @Test
    fun campaignKeepsAuthorityStopAndRevocationVisibleButDiagnosticsFolded() {
        ActivityScenario.launch(CampaignToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(findButton(root, "Authorize CLIR enable campaign"))
                assertTrue(findButton(root, activity.getString(R.string.campaign_ui_start))!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertTrue(findButton(root, activity.getString(R.string.camp_ui_cancel_task))!!.isShown)

                assertNull(root.findViewWithTag<View>("ui-accordion-content:dialer"))
                assertNull(root.findViewWithTag<View>("ui-accordion-content:supervised"))
                assertNull(root.findViewWithTag<View>("ui-accordion-content:campaign-attempt"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("campaign-dialer-card")).isShown)
                assertTrue(requireNotNull(root.findViewWithTag<View>("campaign-supervised-card")).isShown)
                assertTrue(requireNotNull(root.findViewWithTag<View>("campaign-attempt-details")).isShown)
                assertNotNull(root.findViewWithTag<View>("campaign-action-feedback"))
                assertNotNull(root.findViewWithTag<View>("campaign-live-runtime"))
                assertNotNull(root.findViewWithTag<View>("campaign-dialer-role-status"))
                val evidenceBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:evidence"))
                assertEquals(View.GONE, evidenceBody.visibility)
                assertNotNull(findButton(root, activity.getString(R.string.camp_ui_role_button)))
                assertNotNull(findButton(root, activity.getString(R.string.camp_ux05_attach_ai)))
                assertNotNull(findButton(root, activity.getString(R.string.camp_ui_disable_record)))
                assertNotNull(findButton(root, activity.getString(R.string.camp_ui_transcript_short)))

                val toggle = requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:evidence"))
                toggle.performClick()
                assertEquals(View.VISIBLE, evidenceBody.visibility)
                assertTrue(findButton(root, activity.getString(R.string.camp_ui_transcript_short))!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
            }
        }
    }

    private fun findButton(view: View, text: String): Button? {
        if (view is Button && view.text.contains(text)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val result = findButton(view.getChildAt(index), text)
                if (result != null) return result
            }
        }
        return null
    }
}
