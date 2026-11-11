package pl.michalmatu.aicallbridge.conversation

import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPreparationBackNavigationTest {
    @Test fun systemBackOffersStayAndDiscardWithoutSavingTheDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidConversationPreparationStore(context)
        val before = store.load(ConversationPreparation.INCOMING_ID)
        fun assertUnchanged() {
            val current = store.load(ConversationPreparation.INCOMING_ID)
            assertEquals(before.id, current.id)
            assertEquals(before.direction, current.direction)
            assertEquals(before.notes, current.notes)
            assertEquals(before.skills, current.skills)
            assertEquals(before.dataFields, current.dataFields)
        }
        fun dialog(): View = WindowInspector.getGlobalWindowViews().last {
            it.findViewById<View>(android.R.id.button1) != null && it.isShown
        }
        ActivityScenario.launch(ConversationPreparationActivity::class.java).use { scenario ->
            lateinit var edited: ConversationPreparationActivity
            scenario.onActivity { activity ->
                edited = activity
                val notes = activity.window.decorView.findViewWithTag<EditText>("preparation-context-notes")
                val draft = "SYNTHETIC_UNSAVED_CONTEXT"
                notes.setText(draft)
                activity.onBackPressedDispatcher.onBackPressed()
                assertFalse(activity.isFinishing)
                dialog().findViewById<Button>(android.R.id.button2).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val notes = activity.window.decorView.findViewWithTag<EditText>("preparation-context-notes")
                val draft = "SYNTHETIC_UNSAVED_CONTEXT"
                assertEquals(draft, notes.text.toString())
                assertUnchanged()
                activity.onBackPressedDispatcher.onBackPressed()
                dialog().findViewById<Button>(android.R.id.button1).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(edited.isFinishing)
            }
        }
        assertUnchanged()
    }
}
