package pl.michalmatu.aicallbridge.audioengines

import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.MainActivity

@RunWith(AndroidJUnit4::class)
class AndroidModelSelectionNavigationTest {
    @Test fun returningFromCatalogRefreshesModelAndPreservesUnsavedSpeechChoices() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AudioEngineProfileStore(ctx)
        val before = checkNotNull(store.load())
        val next = DialogueModelId.QWEN_3_1_7B
        try {
            ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val views = views(activity.findViewById(android.R.id.content))
                    views.filterIsInstance<RadioButton>().single { it.tag == "PARAKEET_PL" }.performClick()
                    views.filterIsInstance<RadioButton>().single { it.tag == "PIPER_PL" }.performClick()
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(store.save(before.copy(dialogue = next)))
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val radios = views(activity.findViewById(android.R.id.content)).filterIsInstance<RadioButton>()
                    assertTrue("catalog model selection must refresh", radios.single { it.tag == next.name }.isChecked)
                    assertTrue(radios.single { it.tag == "PARAKEET_PL" }.isChecked)
                    assertTrue(radios.single { it.tag == "PIPER_PL" }.isChecked)
                }
            }
            assertTrue(store.save(before))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(store.save(before.copy(dialogue = next)))
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    assertTrue("home must show selected model", views(activity.findViewById(android.R.id.content))
                        .filterIsInstance<TextView>().any { it.text.toString().contains("Model: Qwen3 1.7B") })
                }
            }
        } finally { assertTrue(store.save(before)) }
    }
    private fun views(root: View): List<View> = buildList {
        fun visit(view: View) {
            add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
}
