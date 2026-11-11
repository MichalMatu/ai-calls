package pl.michalmatu.aicallbridge.audioengines

import java.io.ByteArrayInputStream
import pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore
import pl.michalmatu.aicallbridge.textagent.UserModelImportResult
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
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog

@RunWith(AndroidJUnit4::class)
class AndroidModelSelectionNavigationTest {
    @Test fun returningFromCatalogRefreshesModelAndPreservesUnsavedSpeechChoices() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AudioEngineProfileStore(ctx)
        val before = checkNotNull(store.load())
        val library = UserLiteRtModelStore(ctx)
        val imported = library.import("nav-fixture.litertlm",
            ByteArrayInputStream("native-test-fixture".toByteArray()))
        assertTrue(imported is UserModelImportResult.Installed)
        val userModel = (imported as UserModelImportResult.Installed).model
        val next = DialogueModelId.USER_LITERT
        try {
            ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val views = views(activity.findViewById(android.R.id.content))
                    views.filterIsInstance<RadioButton>().single { it.tag == "PARAKEET_PL" }.performClick()
                    views.filterIsInstance<RadioButton>().single { it.tag == "PIPER_PL" }.performClick()
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(store.save(before.copy(dialogue = next, customModelId = userModel.id)))
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val radios = views(activity.findViewById(android.R.id.content)).filterIsInstance<RadioButton>()
                    assertTrue("catalog model selection must refresh",
                        views(activity.findViewById(android.R.id.content))
                            .filterIsInstance<TextView>().any {
                                it.text.toString().contains(userModel.title)
                            })
                    assertTrue(radios.single { it.tag == "PARAKEET_PL" }.isChecked)
                    assertTrue(radios.single { it.tag == "PIPER_PL" }.isChecked)
                }
            }
            assertTrue(store.save(before))
            ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { scenario ->
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(store.save(before.copy(dialogue = next, customModelId = userModel.id)))
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val liveModel = activity.window.decorView.findViewWithTag<TextView>(
                        "usb-value:dialogue")
                    assertNotNull("diagnostics_model_value_missing", liveModel)
                    assertTrue("diagnostics_must_show_fresh_selected_model",
                        liveModel.text.toString().contains("LiteRT-LM"))
                }
            }
        } finally {
            assertTrue(store.save(before))
            assertTrue(library.delete(userModel.id, store.load()?.customModelId))
        }
    }
    private fun views(root: View): List<View> = buildList {
        fun visit(view: View) {
            add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
}
