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
        fun dialog(): View = WindowInspector.getGlobalWindowViews().last {
            it.findViewById<View>(android.R.id.button1) != null && it.isShown
        }
        ActivityScenario.launch(ConversationPreparationActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val notes = activity.window.decorView.findViewWithTag<EditText>("preparation-context-notes")
                val draft = "SYNTHETIC_UNSAVED_CONTEXT"
                notes.setText(draft)
                activity.onBackPressedDispatcher.onBackPressed()
                assertFalse(activity.isFinishing)
                dialog().findViewById<Button>(android.R.id.button2).performClick()
                assertEquals(draft, notes.text.toString())
                assertEquals(before, store.load(ConversationPreparation.INCOMING_ID))
                activity.onBackPressedDispatcher.onBackPressed()
                dialog().findViewById<Button>(android.R.id.button1).performClick()
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, store.load(ConversationPreparation.INCOMING_ID))
    }
}
