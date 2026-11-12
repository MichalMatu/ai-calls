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
import pl.michalmatu.aicallbridge.campaign.AppointmentMvpActivity
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
                assertTrue(findButton(root, activity.getString(R.string.task_new))!!.isShown)
                assertNull(findButton(root, activity.getString(R.string.ui_campaigns)))
                assertTrue(findButton(root, activity.getString(R.string.task_stop_all))!!.isShown)
                assertNull(findButton(root, "Rozmowy i nagrania"))
                assertNull(findButton(root, "Laboratorium offline"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:history")).isShown)
                assertNull(root.findViewWithTag<View>("app-tab:lab"))
                assertNull(findButton(root, activity.getString(R.string.home_ui_one_tap)))
                assertNotNull(root.findViewWithTag<View>("app-tab:settings"))
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("home-action-feedback")).visibility)
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

    @Test fun appointmentOffersOneScopedStartAndSafeCancellationWithoutCampaignControls() {
        ActivityScenario.launch(AppointmentMvpActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("appointment-authorize-call"))
                assertNotNull(root.findViewWithTag<View>("appointment-sim-picker"))
                assertNotNull(root.findViewWithTag<View>("appointment-review-result"))
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertNull(findButton(root, activity.getString(R.string.ui_campaigns)))
                assertNull(findButton(root, activity.getString(R.string.camp_ux05_attach_ai)))
                assertNull(root.findViewWithTag<View>("campaign-primary-actions"))
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
