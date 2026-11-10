package pl.michalmatu.aicallbridge

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity

/** Geometry and navigation only. Never triggers CALL, STOP, grant, download, or save. */
@RunWith(AndroidJUnit4::class)
class AndroidUx07CardSpacingContractTest {
    @Test fun homeCardsKeepReadableSpacingAndNoCampaignUi() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                for (tag in listOf("home-new-call-card", "home-call-status-card")) {
                    assertNotNull(root.findViewWithTag<View>(tag))
                }
                val card = requireNotNull(root.findViewWithTag<View>("home-new-call-card"))
                val params = requireNotNull(card.layoutParams as? LinearLayout.LayoutParams)
                assertTrue("home_card_gap", params.bottomMargin >=
                    (16 * activity.resources.displayMetrics.density).toInt())
                assertNull(root.findViewWithTag<View>("campaign-primary-actions"))
                assertNull(root.findViewWithTag<View>("app-tab:lab"))
            }
        }
    }

    @Test fun modelsExposeParakeetAndDialogueInOneRootScreen() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:settings")).isSelected)
                assertNotNull(root.findViewWithTag<View>("parakeet-transfer-card"))
                assertNotNull(root.findViewWithTag<View>("model-card:GEMMA_4_E2B"))
            }
        }
    }
}
