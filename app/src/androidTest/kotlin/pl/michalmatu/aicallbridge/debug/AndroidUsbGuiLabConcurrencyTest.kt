package pl.michalmatu.aicallbridge.debug

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity

/**
 * Physical, offline shared-backend admission test. A real on-screen Lab session
 * takes the native LiteRT lease. The USB receiver's underlying controller must
 * fail its competing worker without creating a second native backend. After
 * GUI STOP/cleanup, the USB controller must be able to acquire and release it.
 *
 * No call, microphone, user message, profile mutation or transcript export.
 */
@RunWith(AndroidJUnit4::class)
class AndroidUsbGuiLabConcurrencyTest {
    private fun eventually(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (predicate()) return true
            Thread.sleep(250)
        }
        return predicate()
    }

    private fun guiReady(scenario: ActivityScenario<ConversationSkillLabActivity>): Boolean {
        val ready = AtomicBoolean(false)
        scenario.onActivity { activity ->
            val label = activity.window.decorView.findViewWithTag<TextView>("lab-status")
            ready.set(label?.text?.toString() == activity.getString(R.string.lab_manual_ready))
        }
        return ready.get()
    }

    private fun guiCanRestart(scenario: ActivityScenario<ConversationSkillLabActivity>): Boolean {
        val enabled = AtomicBoolean(false)
        scenario.onActivity { activity ->
            enabled.set(activity.window.decorView.findViewWithTag<View>("lab-start")?.isEnabled == true)
        }
        return enabled.get()
    }

    @Test fun guiNativeLeaseRefusesUsbWorkerThenUsbStartsAfterGuiStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = UsbLabControl.execute(context, "status", JSONObject())
        assertTrue(before.ok)
        assertTrue("requires_idle_phone", before.data.getBoolean("call_idle"))
        assertFalse("requires_no_active_campaign", before.data.getBoolean("outgoing_ai_running"))
        assertFalse("requires_no_live_chat", before.data.getBoolean("live_chat_active"))
        val initialLab = before.data.getJSONObject("lab")
        assertTrue("requires_idle_usb_lab", !initialLab.getBoolean("process_session_active") ||
            initialLab.optString("phase") in setOf("STOPPED", "COMPLETED", "FAILED", "NONE"))

        var usbSessionId: String? = null
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    val start = requireNotNull(activity.window.decorView
                        .findViewWithTag<View>("lab-start"))
                    assertTrue(start.isEnabled)
                    start.performClick()
                }
                assertTrue("gui_native_init_timeout",
                    eventually(160_000) { guiReady(scenario) })

                val competing = UsbLabControl.execute(context, "lab_start",
                    JSONObject().put("context_tokens", 4_096).put("preset", "natural"))
                assertTrue("usb_controller_admitted_worker", competing.ok)
                usbSessionId = competing.data.getString("session_id")
                assertTrue("usb_competing_worker_not_rejected", eventually(80_000) {
                    UsbLabControl.execute(context, "lab_status", JSONObject())
                        .data.optString("phase") == "FAILED"
                })
                val refused = UsbLabControl.execute(context, "lab_status", JSONObject())
                assertEquals("another_lab_model_session_active",
                    refused.data.optString("error_category"))
                assertTrue("gui_still_has_native_lease", guiReady(scenario))

                scenario.onActivity { activity ->
                    val stop = requireNotNull(activity.window.decorView
                        .findViewWithTag<View>("lab-stop"))
                    assertTrue("gui_stop_not_visible", stop.isShown)
                    stop.performClick()
                }
                assertTrue("gui_native_cleanup_timeout",
                    eventually(80_000) { guiCanRestart(scenario) })

                val usbAfterRelease = UsbLabControl.execute(context, "lab_start",
                    JSONObject().put("context_tokens", 4_096).put("preset", "natural"))
                assertTrue("usb_backend_not_reopened", usbAfterRelease.ok)
                usbSessionId = usbAfterRelease.data.getString("session_id")
                assertTrue("usb_native_init_after_gui_cleanup_timeout",
                    eventually(160_000) {
                        UsbLabControl.execute(context, "lab_status", JSONObject())
                            .data.optString("phase") in setOf("INPUT_READY", "FAILED")
                    })
                val ready = UsbLabControl.execute(context, "lab_status", JSONObject())
                assertEquals("usb_native_backend_failed_after_gui_stop",
                    "INPUT_READY", ready.data.optString("phase"))
                assertEquals(0, ready.data.optInt("turns"))
                val stop = UsbLabControl.execute(context, "lab_stop",
                    JSONObject().put("session_id", usbSessionId))
                assertTrue("usb_stop_after_reacquire", stop.ok)
                val after = UsbLabControl.execute(context, "status", JSONObject())
                assertTrue(after.ok)
                assertTrue("call_no_longer_idle", after.data.getBoolean("call_idle"))
                println("S22_UI_USB_NATIVE_LEASE=PASS;GUI_BUSY_REFUSAL=True;USB_REOPEN=True;CALL_IDLE=True")
            } finally {
                usbSessionId?.let {
                    UsbLabControl.execute(context, "lab_stop",
                        JSONObject().put("session_id", it))
                }
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<View>("lab-stop")
                        ?.takeIf { it.isShown }?.performClick()
                }
            }
        }
    }
}
