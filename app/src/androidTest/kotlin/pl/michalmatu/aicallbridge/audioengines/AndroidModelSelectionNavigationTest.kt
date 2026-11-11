package pl.michalmatu.aicallbridge.audioengines

import android.os.Build
import android.view.inspector.WindowInspector
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.textagent.UserLiteRtModelStore
import pl.michalmatu.aicallbridge.textagent.UserModelImportResult

@RunWith(AndroidJUnit4::class)
class AndroidModelSelectionNavigationTest {
    @Test fun oneEditorPreservesDraftAndDiagnosticsRefreshTheSavedModel() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AudioEngineProfileStore(context)
        val before = checkNotNull(store.load())
        val library = UserLiteRtModelStore(context)
        val result = library.import("navigation-fixture.litertlm", ByteArrayInputStream("synthetic-not-a-model".toByteArray()))
        val imported = (result as UserModelImportResult.Installed).model
        try {
            assertTrue(store.save(AudioEngineProfile.STABLE))
            ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<View>("model-voice-inputs").performClick()
                    WindowInspector.getGlobalWindowViews().last {
                        it.isShown && it.findViewWithTag<View>("GEMMA_AUDIO_DIRECT") != null
                    }.findViewWithTag<RadioButton>("GEMMA_AUDIO_DIRECT").performClick()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(SpeechInputId.GEMMA_AUDIO_DIRECT, store.load()?.input)
                    assertEquals(activity.getString(pl.michalmatu.aicallbridge.R.string.mv_gemma_short),
                        activity.window.decorView.findViewWithTag<TextView>("model-voice-input-choice")
                            .text.toString())
                    assertNull(activity.window.decorView.findViewWithTag<View>("model-voice-advanced"))
                }
            }
            ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { scenario ->
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(store.save(before.copy(input = SpeechInputId.ANDROID_STT,
                    dialogue = DialogueModelId.USER_LITERT, customModelId = imported.id)))
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val text = activity.window.decorView.findViewWithTag<TextView>("usb-value:dialogue").text.toString()
                    assertTrue(text.contains("LiteRT-LM") || text.contains("USER_LITERT"))
                }
            }
        } finally {
            assertTrue(store.save(before))
            assertTrue(library.delete(imported.id, store.load()?.customModelId))
        }
    }
}
