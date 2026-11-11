package pl.michalmatu.aicallbridge.textagent

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity

@RunWith(AndroidJUnit4::class)
class AndroidOwnerModelLibraryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun importSelectAndDeleteNeverTouchProtectedBaseline() {
        val library = UserLiteRtModelStore(context)
        val store = AudioEngineProfileStore(context)
        val before = checkNotNull(store.load())
        val base = Gemma4LiteRtTextBackendFactory.modelFile(context, DialogueModelId.GEMMA_4_E2B)
        val baselineBytes = if (base.exists()) base.length() else -1L
        val imported = library.import("owner-test.litertlm", ByteArrayInputStream("sandbox-only".toByteArray()))
        assertTrue(imported is UserModelImportResult.Installed)
        val model = (imported as UserModelImportResult.Installed).model
        try {
            assertTrue(library.isInstalled(model))
            assertEquals(model, library.find(model.id))
            assertNotEquals(base.absolutePath, library.file(model).absolutePath)
            assertTrue(store.save(before.copy(dialogue = DialogueModelId.USER_LITERT,
                customModelId = model.id)))
            assertEquals(library.file(model).canonicalPath,
                Gemma4LiteRtTextBackendFactory.modelFile(context).canonicalPath)
            assertFalse(library.delete(model.id, model.id))
            assertTrue(library.file(model).exists())
        } finally {
            assertTrue(store.save(before))
            assertTrue(library.delete(model.id, store.load()?.customModelId))
        }
        assertFalse(library.file(model).exists())
        assertEquals(baselineBytes, if (base.exists()) base.length() else -1L)
    }

    @Test fun rejectsUnsupportedFilesAndInterruptedImportsWithoutPublishing() {
        val library = UserLiteRtModelStore(context)
        assertTrue(library.import("weights.gguf",
            ByteArrayInputStream("not-litert".toByteArray())) is UserModelImportResult.Failure)
        assertTrue(library.import("cancelled.litertlm",
            ByteArrayInputStream("test".toByteArray()), cancelled = { true })
                is UserModelImportResult.Failure)
    }

    @Test fun modelsScreenExposesGemmaAndFileImportNotRetiredCatalogCards() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("model-card:GEMMA_4_E2B"))
                assertNotNull(root.findViewWithTag<Button>("model-import-user"))
                assertNull(root.findViewWithTag<View>("model-card:GEMMA_4_E4B"))
                assertNull(root.findViewWithTag<View>("model-card:QWEN_3_1_7B"))
            }
        }
    }

    @Test fun diagnosticDetailsAreReadableAndExactJsonCanBeCopied() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val details = root.findViewWithTag<LinearLayout>("usb-technical-fields")
                val copy = root.findViewWithTag<Button>("usb-copy-json")
                assertNotNull(details)
                assertTrue(details.childCount > 0)
                assertTrue(copy.isEnabled)
                copy.performClick()
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()
                assertNotNull(raw)
                val parsed = JSONObject(raw!!)
                assertTrue(parsed.has("call_idle"))
                assertTrue(parsed.has("dialogue"))
                assertTrue(parsed.has("private_transcript_policy"))
            }
        }
    }
}
