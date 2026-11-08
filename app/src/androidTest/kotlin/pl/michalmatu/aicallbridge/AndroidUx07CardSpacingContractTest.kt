package pl.michalmatu.aicallbridge

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/** Geometry and navigation only. Never triggers CALL, STOP, grant, download, or save. */
@RunWith(AndroidJUnit4::class)
class AndroidUx07CardSpacingContractTest {
    @Test fun adjacentCampaignCardsKeepSixteenDpGaps() {
        ActivityScenario.launch(CampaignToolActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var cards = 0
                fun visit(view: View) {
                    if (view !is ViewGroup) return
                    if (view is LinearLayout && view.orientation == LinearLayout.VERTICAL) {
                        for (index in 0 until view.childCount) {
                            val child = view.getChildAt(index)
                            if (child.getTag(R.id.ai_calls_ui_card_marker) == true) {
                                cards++
                                val params = checkNotNull(child.layoutParams as? LinearLayout.LayoutParams)
                                assertTrue("card_gap_missing", params.bottomMargin >=
                                    (16 * activity.resources.displayMetrics.density).toInt())
                            }
                        }
                    }
                    for (index in 0 until view.childCount) visit(view.getChildAt(index))
                }
                visit(activity.window.decorView)
                assertTrue("campaign_cards_missing", cards >= 5)
            }
        }
    }

    @Test fun modelsExposeParakeetAndDialogueInOneRootScreen() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:models")).isSelected)
                assertNotNull(root.findViewWithTag<View>("parakeet-transfer-card"))
                assertNotNull(root.findViewWithTag<View>("model-card:GEMMA_4_E2B"))
            }
        }
    }
}
