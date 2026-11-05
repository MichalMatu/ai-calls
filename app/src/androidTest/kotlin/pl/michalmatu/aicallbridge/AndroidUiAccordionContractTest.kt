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
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity

/** Read-only UI checks. Neither case clicks authorization, dial, STOP, or permissions. */
@RunWith(AndroidJUnit4::class)
class AndroidUiAccordionContractTest {
    @Test
    fun homeKeepsEssentialActionsVisibleAndPreservesEveryFoldedControl() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(findButton(root, "Rozpocznij autoryzowane zadanie AI")!!.isShown)
                assertTrue(findButton(root, "Campaign tools")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertNotNull(findButton(root, "Jeden przycisk:"))

                val audioBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:audio"))
                val gemmaBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:gemma"))
                val setupBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:setup"))
                val diagnosticBody = requireNotNull(root.findViewWithTag<View>("ui-accordion-content:diagnostics"))
                assertEquals(View.GONE, audioBody.visibility)
                assertEquals(View.GONE, gemmaBody.visibility)
                assertEquals(View.GONE, setupBody.visibility)
                assertEquals(View.GONE, diagnosticBody.visibility)
                assertFalse(findButton(root, "Jeden przycisk:")!!.isShown)
                assertNotNull(findButton(root, "Modele: pobierz, importuj, wybierz"))
                assertNotNull(findButton(root, "Grant microphone permission"))
                assertNotNull(findButton(root, "Enroll phone number"))
                assertNotNull(findButton(root, "Run device capability probe"))

                val toggle = requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:gemma"))
                assertTrue(toggle.contentDescription.contains("Rozwiń"))
                toggle.performClick()
                assertEquals(View.VISIBLE, gemmaBody.visibility)
                assertTrue(findButton(root, "Modele: pobierz, importuj, wybierz")!!.isShown)
                assertTrue(toggle.contentDescription.contains("Zwiń"))
                toggle.performClick()
                assertEquals(View.GONE, gemmaBody.visibility)
                val diagnosticToggle = requireNotNull(
                    root.findViewWithTag<Button>("ui-accordion-toggle:diagnostics"),
                )
                diagnosticToggle.performClick()
                assertTrue(findButton(root, "Jeden przycisk:")!!.isShown)
                assertTrue(findButton(root, "Rozpocznij autoryzowane zadanie AI")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
            }
        }
    }

    @Test
    fun campaignKeepsAuthorityStopAndRevocationVisibleButDiagnosticsFolded() {
        ActivityScenario.launch(CampaignToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(findButton(root, "Authorize CLIR enable campaign")!!.isShown)
                assertTrue(findButton(root, "Start authorized CLIR enable")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)
                assertTrue(findButton(root, "Revoke campaign grant")!!.isShown)

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
