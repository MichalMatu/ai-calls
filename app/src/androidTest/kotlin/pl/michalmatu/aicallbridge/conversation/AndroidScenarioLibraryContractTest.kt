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
                val edit = requireNotNull(root.findViewWithTag<View>("scenario-preset:mechanic"))
                val button = findButton(edit)
                assertNotNull(button)
                button!!.performClick()
                val current = activity.window.decorView
                assertNotNull(current.findViewWithTag<View>("scenario-form"))
                for (id in listOf("visit_reason", "preferred_time", "budget", "additional_instructions"))
                    assertNotNull(current.findViewWithTag<View>("scenario-input:$id"))
                assertNotNull(current.findViewWithTag<View>("scenario-form-status"))
            }
        }
    }

    private fun findButton(view: View): android.widget.Button? {
        if (view is android.widget.Button) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                findButton(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
