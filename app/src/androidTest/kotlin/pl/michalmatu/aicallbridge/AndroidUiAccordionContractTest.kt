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
    fun homeGroupsSettingsByPurposeWithoutMutatingRuntimeOrSkillVersions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val runtimeBefore = CallRuntimePreferences(activity).load()
                val skillsBefore = AndroidConversationPreparationStore(activity).loadSkillLibrary()

                assertTrue(findButton(root, "Rozpocznij autoryzowane zadanie AI")!!.isShown)
                assertTrue(findButton(root, "Campaign tools")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)

                val ids = listOf("basic", "secretary", "skills", "laboratory", "advanced")
                val bodies = ids.associateWith { id ->
                    requireNotNull(root.findViewWithTag<View>("ui-accordion-content:$id"))
                }
                assertEquals(View.VISIBLE, bodies.getValue("basic").visibility)
                ids.drop(1).forEach { assertEquals(View.GONE, bodies.getValue(it).visibility) }

                assertTrue(findButton(root, "Modele: pobierz, importuj, wybierz")!!.isShown)
                assertTrue(findButton(root, "Silniki: STT / model / TTS")!!.isShown)
                assertTrue(findButton(root, "Grant microphone permission")!!.isShown)
                assertTrue(findButton(root, "Enroll phone number")!!.isShown)
                assertNotNull(findButton(root, "Odbieranie: AI / ręcznie"))
                assertNotNull(findButton(root, "Skills i dane rozmów"))
                assertNotNull(findButton(root, "Skills i presety Gemmy"))
                assertNotNull(findButton(root, "Laboratorium skillsów"))
                assertNotNull(findButton(root, "Narzędzia AI"))
                assertNotNull(findButton(root, "Run device capability probe"))
                assertFalse(findButton(root, "Jeden przycisk:")!!.isShown)

                ids.drop(1).forEach { id ->
                    val toggle = requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:$id"))
                    assertTrue(toggle.contentDescription.contains("Rozwiń"))
                    toggle.performClick()
                    assertEquals(View.VISIBLE, bodies.getValue(id).visibility)
                    assertTrue(toggle.contentDescription.contains("Zwiń"))
                    toggle.performClick()
                    assertEquals(View.GONE, bodies.getValue(id).visibility)
                }

                val advanced = requireNotNull(root.findViewWithTag<Button>("ui-accordion-toggle:advanced"))
                advanced.performClick()
                assertTrue(findButton(root, "Jeden przycisk:")!!.isShown)
                assertTrue(findButton(root, "STOP AI")!!.isShown)

                val runtimeAfter = CallRuntimePreferences(activity).load()
                val skillsAfter = AndroidConversationPreparationStore(activity).loadSkillLibrary()
                assertEquals(runtimeBefore.audioMode, runtimeAfter.audioMode)
                assertEquals(runtimeBefore.textLlmProvider, runtimeAfter.textLlmProvider)
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
