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
    @Test fun newScenarioStartsBlankAndDoesNotAutoSave() {
        ActivityScenario.launch(ScenarioLibraryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                requireNotNull(root.findViewWithTag<View>("scenario-new")).performClick()
                val editor = activity.window.decorView
                val title = requireNotNull(editor.findViewWithTag<android.widget.EditText>(
                    "scenario-personal-name"))
                val goal = requireNotNull(editor.findViewWithTag<android.widget.EditText>(
                    "scenario-personal-goal"))
                assertEquals("", title.text.toString())
                assertEquals("", goal.text.toString())
                assertNotNull(editor.findViewWithTag<View>(
                    "scenario-input:additional_instructions"))
                assertNotNull(editor.findViewWithTag<View>("scenario-save"))
                assertNull("prepare_requires_saved_new_scenario",
                    editor.findViewWithTag<View>("scenario-prepare-outgoing"))
                assertNull(editor.findViewWithTag<View>("scenario-more"))
                requireNotNull(editor.findViewWithTag<View>("app-child-back")).performClick()
                assertNotNull(activity.window.decorView.findViewWithTag<View>("scenario-new"))
            }
        }
    }

    @Test fun mechanicPresetOpensEditableFormWithoutStartingCall() {
        ActivityScenario.launch(ScenarioLibraryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val templates = requireNotNull(root.findViewWithTag<View>("scenario-presets-container"))
                assertFalse("templates_should_start_collapsed", templates.isShown)
                requireNotNull(root.findViewWithTag<View>("scenario-presets-toggle")).performClick()
                assertTrue("templates_should_expand", templates.isShown)
                assertNotNull(root.findViewWithTag<View>("scenario-new"))
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
