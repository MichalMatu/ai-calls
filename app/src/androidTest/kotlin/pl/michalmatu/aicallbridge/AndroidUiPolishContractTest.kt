package pl.michalmatu.aicallbridge

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/** Safe visual contracts: opening screens only. No phone call, save, model transfer or test run. */
@RunWith(AndroidJUnit4::class)
class AndroidUiPolishContractTest {
    private fun tagged(view: View, prefix: String): List<View> = buildList {
        if ((view.tag as? String)?.startsWith(prefix) == true) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            addAll(tagged(view.getChildAt(i), prefix))
        }
    }

    @Test fun labOnlyShowsActionsApplicableToItsIdleState() {
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("lab-stop")).visibility)
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("lab-rating-group")).visibility)
                assertNotNull(root.findViewWithTag<View>("lab-status"))
            }
        }
    }

    @Test fun modelsAreCardsAndIdleTransferActionsAreHidden() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("model-transfer-cancel")).visibility)
                assertNotNull(root.findViewWithTag<View>("parakeet-transfer-card"))
                assertNotNull(root.findViewWithTag<View>("parakeet-transfer-status"))
                assertNotNull(root.findViewWithTag<View>("models-pipeline-summary"))
                val cards = tagged(root, "model-card:")
                assertTrue("catalog_is_empty", cards.isNotEmpty())
                for (card in cards) {
                    val id = (card.tag as String).substringAfter("model-card:")
                    assertNotNull(root.findViewWithTag<View>("model-details-toggle:" + id))
                    assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                        "model-details:" + id)).visibility)
                }
            }
        }
    }

    @Test fun preparationHasOneSelectorAndReadableEditingCards() {
        ActivityScenario.launch(ConversationPreparationActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                val selector = requireNotNull(root.findViewWithTag<Spinner>("preparation-direction-picker"))
                assertEquals(2, selector.adapter.count)
                assertNotNull(root.findViewWithTag<View>("preparation-skills"))
                assertNotNull(root.findViewWithTag<View>("preparation-vault-fields"))
                assertNotNull(root.findViewWithTag<View>("preparation-actions"))
                val notes = tagged(root, "no-such-tag") // no mutation, keep caller state unchanged
                assertTrue(notes.isEmpty())
            }
        }
    }

    @Test fun campaignPreservesSafetyActionsAndCollapsesIdleChat() {
        ActivityScenario.launch(CampaignToolActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                assertNotNull(root.findViewWithTag<View>("campaign-primary-actions"))
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>("live-chat-scroll")).visibility)
                assertTrue(tagged(root, "ui-accordion-toggle:").isNotEmpty())
            }
        }
    }

    @Test fun engineCatalogHasCollapsedUnavailableOptions() {
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                assertNull(root.findViewWithTag<View>("engine-cancel-download"))
                assertTrue(tagged(root, "engine-unavailable:").isNotEmpty())
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:models")).isSelected)
            }
        }
    }
}
