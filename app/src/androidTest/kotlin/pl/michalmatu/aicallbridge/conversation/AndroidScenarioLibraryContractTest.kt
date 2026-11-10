package pl.michalmatu.aicallbridge.conversation

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only form navigation, no Save, CALL, grant or personal data mutation. */
@RunWith(AndroidJUnit4::class)
class AndroidScenarioLibraryContractTest {
    @Test fun mechanicPresetOpensEditableFormWithoutStartingCall() {
        ActivityScenario.launch(ScenarioLibraryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("scenario-preset:mechanic"))
                val card = requireNotNull(root.findViewWithTag<View>("scenario-preset:mechanic"))
                assertTrue("scenario_card_not_clickable", card.isClickable)
                card.performClick()
                val current = activity.window.decorView
                assertNotNull(current.findViewWithTag<View>("scenario-form"))
                for (id in listOf("visit_reason", "preferred_time", "budget", "additional_instructions"))
                    assertNotNull(current.findViewWithTag<View>("scenario-input:$id"))
                assertNotNull(current.findViewWithTag<View>("scenario-form-status"))
                val back = requireNotNull(current.findViewWithTag<View>("app-child-back"))
                back.performClick()
                assertNotNull(activity.window.decorView.findViewWithTag<View>(
                    "scenario-preset:mechanic"))
            }
        }
    }

}
