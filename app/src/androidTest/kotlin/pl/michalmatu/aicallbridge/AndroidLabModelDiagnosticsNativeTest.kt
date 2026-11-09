package pl.michalmatu.aicallbridge

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity

/** Real E2B Lab timing and next-session context UI; no Call, profile changes or export. */
@RunWith(AndroidJUnit4::class)
class AndroidLabModelDiagnosticsNativeTest {
    private fun eventually(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (predicate()) return true
            Thread.sleep(200)
        }
        return predicate()
    }

    @Test fun contextSelectionAndColdStartTimingReachRealModelOnS22() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = app.getSharedPreferences("lab-diagnostics", Context.MODE_PRIVATE)
        val hadValue = prefs.contains("context_tokens")
        val oldValue = prefs.getInt("context_tokens", 4_096)
        try {
            ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scene ->
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    val context = requireNotNull(root.findViewWithTag<EditText>("lab-context-tokens"))
                    val diagnostics = requireNotNull(root.findViewWithTag<TextView>("lab-model-diagnostics"))
                    assertTrue(context.isEnabled)
                    assertTrue(diagnostics.text.contains("4096") || diagnostics.text.isNotEmpty())
                    context.setText("2048")
                    val start = requireNotNull(root.findViewWithTag<View>("lab-start"))
                    assertTrue(start.isEnabled)
                    start.performClick()
                }
                assertTrue("lab_init_timeout", eventually(175_000) {
                    var ready = false
                    scene.onActivity { activity ->
                        ready = activity.window.decorView.findViewWithTag<View>(
                            "lab-manual-send")?.isEnabled == true
                    }
                    ready
                })
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    val context = requireNotNull(root.findViewWithTag<EditText>("lab-context-tokens"))
                    assertFalse("context_must_be_snapshot_during_run", context.isEnabled)
                    val input = requireNotNull(root.findViewWithTag<EditText>("lab-manual-input"))
                    input.setText("Dzień dobry. Odpowiedz jednym krótkim zdaniem po polsku.")
                    root.findViewWithTag<View>("lab-manual-send")!!.performClick()
                }
                assertTrue("native_timing_timeout", eventually(190_000) {
                    var measured = false
                    scene.onActivity { activity ->
                        val text = activity.window.decorView
                            .findViewWithTag<TextView>("lab-model-diagnostics")
                            ?.text?.toString().orEmpty()
                        measured = text.contains("2048") &&
                            (text.contains("GPU") || text.contains("CPU")) &&
                            !text.contains("NaN")
                    }
                    measured
                })
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    val text = requireNotNull(root.findViewWithTag<TextView>(
                        "lab-model-diagnostics")).text.toString()
                    assertTrue("model_identity_missing", text.contains("GEMMA_4_E2B"))
                    assertTrue("selected_context_missing", text.contains("2048"))
                    assertTrue("engine_backend_missing", text.contains("GPU") || text.contains("CPU"))
                    val stop = requireNotNull(root.findViewWithTag<View>("lab-stop"))
                    assertTrue("lab_stop_missing", stop.isShown)
                    stop.performClick()
                    println("S22_LAB_DIAGNOSTICS_NATIVE_CONTEXT2048=PASS")
                }
            }
        } finally {
            val editor = prefs.edit()
            if (hadValue) editor.putInt("context_tokens", oldValue)
            else editor.remove("context_tokens")
            assertTrue("restore_original_lab_budget", editor.commit())
        }
    }
}
