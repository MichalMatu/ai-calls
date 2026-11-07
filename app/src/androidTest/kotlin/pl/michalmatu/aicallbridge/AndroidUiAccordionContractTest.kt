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
                assertTrue(findButton(root, "Rozpocznij autoryzowane zadanie AI")!!.isShown)
                assertTrue(findButton(root, "Kampanie")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertTrue(findButton(root, "Rozmowy i nagrania")!!.isShown)
                assertTrue(findButton(root, "Laboratorium offline")!!.isShown)
                assertTrue(findButton(root, "Jeden przycisk:")!!.isShown)
                assertNotNull(root.findViewWithTag<View>("app-tab:settings"))
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
                assertTrue(findButton(root, "Rozpocznij zadanie: włącz CLIR — skrypt + model")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertTrue(findButton(root, "Anuluj zadanie")!!.isShown)

                val dialerBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:dialer"))
                val supervisedBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:supervised"))
                val evidenceBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:evidence"))
                assertEquals(View.GONE, dialerBody.visibility)
                assertEquals(View.GONE, supervisedBody.visibility)
                assertEquals(View.GONE, evidenceBody.visibility)
                assertNotNull(findButton(root, "Włącz autonomiczne połączenia"))
                assertNotNull(findButton(root, "Uruchom AI w trwającej rozmowie"))
                assertNotNull(findButton(root, "Wyłącz nagrywanie"))
                assertNotNull(findButton(root, "Pokaż transkrypt"))

                val toggle = requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:evidence"))
                toggle.performClick()
                assertEquals(View.VISIBLE, evidenceBody.visibility)
                assertTrue(findButton(root, "Pokaż transkrypt")!!.isShown)
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
