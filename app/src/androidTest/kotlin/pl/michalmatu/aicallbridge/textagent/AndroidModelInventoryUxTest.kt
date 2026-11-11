package pl.michalmatu.aicallbridge.textagent

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.*

/** Synthetic files on an isolated emulator only; no native model load, network, audio or Call. */
@RunWith(AndroidJUnit4::class)
class AndroidModelInventoryUxTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Before fun emulatorOnly() {
        assumeTrue("isolated_emulator_required", Build.HARDWARE in setOf("ranchu", "goldfish"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_PHONE_STATE)
    }

    private fun retired(id: DialogueModelId): File = Gemma4LiteRtTextBackendFactory.modelFile(context, id)

    private fun createSynthetic(file: File, text: String) {
        check(!file.exists()) { "existing_model_must_be_preserved" }
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        file.writeText(text)
    }

    private fun dialog(): View = WindowInspector.getGlobalWindowViews().last {
        it.findViewById<View>(android.R.id.button1) != null && it.isShown
    }

    @Test fun absentLegacyFilesHaveNoDeletionActionAndMissingParakeetCannotBeUsed() {
        assertFalse(ParakeetModelFile.resolve(context).exists())
        val before = AudioEngineProfileStore(context).load()
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("models-clean-legacy"))
                assertNull(root.findViewWithTag<View>("stored-model-delete:GEMMA_4_E4B"))
                assertEquals(View.GONE, root.findViewWithTag<Button>("parakeet-delete").visibility)
                assertEquals(View.GONE, root.findViewWithTag<Button>("parakeet-select-engine").visibility)
                val status = root.findViewWithTag<TextView>("parakeet-transfer-status").text.toString()
                assertTrue(status.contains(activity.getString(R.string.ux07_parakeet_missing)))
                assertTrue(status.contains(activity.getString(R.string.models_ui_download_size, "").trim()))
                assertNotNull(root.findViewWithTag<TextView>("models-storage-summary"))
            }
        }
        assertEquals(before, AudioEngineProfileStore(context).load())
        assertFalse(ParakeetModelTransferService.status.busy)
    }

    @Test fun confirmationAndDeleteReferOnlyToTheChosenStoredFile() {
        val chosen = retired(DialogueModelId.GEMMA_4_E4B)
        val other = retired(DialogueModelId.QWEN_3_0_6B)
        createSynthetic(chosen, "synthetic retired model one")
        try {
            createSynthetic(other, "synthetic retired model two")
            try {
                ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        val root = activity.window.decorView
                        assertNotNull(root.findViewWithTag<View>("stored-model:GEMMA_4_E4B"))
                        assertNotNull(root.findViewWithTag<View>("stored-model:QWEN_3_0_6B"))
                        assertNull(root.findViewWithTag<View>("stored-model:QWEN_3_1_7B"))
                        root.findViewWithTag<Button>("stored-model-delete:GEMMA_4_E4B").performClick()
                        val message = dialog().findViewById<TextView>(android.R.id.message).text.toString()
                        assertTrue(message.contains(LanguageModelCatalog[DialogueModelId.GEMMA_4_E4B].title))
                        assertFalse(message.contains(LanguageModelCatalog[DialogueModelId.QWEN_3_0_6B].title))
                        dialog().findViewById<Button>(android.R.id.button2).performClick()
                        assertTrue(chosen.exists())
                        assertTrue(other.exists())
                        root.findViewWithTag<Button>("stored-model-delete:GEMMA_4_E4B").performClick()
                        dialog().findViewById<Button>(android.R.id.button1).performClick()
                        assertFalse(chosen.exists())
                        assertTrue(other.exists())
                        assertNull(root.findViewWithTag<View>("stored-model:GEMMA_4_E4B"))
                        assertNotNull(root.findViewWithTag<View>("stored-model:QWEN_3_0_6B"))
                    }
                }
            } finally { other.delete() }
        } finally { chosen.delete() }
    }

    @Test fun selectingParakeetWhileDeletionDialogIsOpenPreservesItsFile() {
        val file = ParakeetModelFile.resolve(context)
        val store = AudioEngineProfileStore(context)
        val before = checkNotNull(store.load())
        createSynthetic(file, "incomplete synthetic Parakeet")
        try {
            assertTrue(store.save(before.copy(input = SpeechInputId.ANDROID_STT)))
            ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertFalse(root.findViewWithTag<Button>("parakeet-select-engine").isEnabled)
                    root.findViewWithTag<Button>("parakeet-delete").performClick()
                    val message = dialog().findViewById<TextView>(android.R.id.message).text.toString()
                    assertTrue(message.contains("Parakeet"))
                    assertTrue(store.save(before.copy(input = SpeechInputId.PARAKEET_PL)))
                    dialog().findViewById<Button>(android.R.id.button1).performClick()
                    assertTrue(file.exists())
                    assertFalse(root.findViewWithTag<Button>("parakeet-delete").isEnabled)
                    val state = root.findViewWithTag<TextView>("parakeet-transfer-status").text.toString()
                    assertTrue(state.contains(activity.getString(R.string.ux08_parakeet_wrong_size)))
                    assertTrue(state.contains(activity.getString(R.string.models_ui_selected_next)))
                }
            }
        } finally {
            assertTrue(store.save(before))
            file.delete()
        }
    }

    @Test fun savingMissingParakeetGivesADownloadRemedyWithoutChangingTheProfile() {
        assertFalse(ParakeetModelFile.resolve(context).exists())
        val store = AudioEngineProfileStore(context)
        val before = store.load()
        ActivityScenario.launch(AudioEngineSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                root.findViewWithTag<View>("PARAKEET_PL").performClick()
                root.findViewWithTag<Button>("engine-save").performClick()
                assertTrue(root.findViewWithTag<TextView>("engine-feedback").text.toString()
                    .contains(activity.getString(R.string.models_ui_parakeet_required)))
                assertEquals(before, store.load())
            }
        }
    }
}
