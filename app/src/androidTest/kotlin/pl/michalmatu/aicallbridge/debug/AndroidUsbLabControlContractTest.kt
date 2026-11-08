package pl.michalmatu.aicallbridge.debug

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DUMP-protected USB Lab channel must be runnable off-call with no task grant,
 * while intervention requests refuse to act without an exact owned Call.
 */
@RunWith(AndroidJUnit4::class)
class AndroidUsbLabControlContractTest {
    @Suppress("DEPRECATION")
    @Test fun exportedReceiverRequiresDumpAndDebugRuntime() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = app.packageManager.getReceiverInfo(
            ComponentName(app, UsbLabControlReceiver::class.java), 0)
        assertTrue(receiver.exported)
        assertEquals("android.permission.DUMP", receiver.permission)
        assertTrue(app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    }

    @Test fun statusWithoutPhoneCallDoesNotAuthorizeTelephony() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            throw AssertionError("offcall_test_requires_read_phone_state")
        }
        val telecom = app.getSystemService(TelecomManager::class.java)
        assertFalse("offcall_test_requires_idle_phone", telecom.isInCall)
        val status = UsbLabControl.execute(app, "status", JSONObject())
        assertTrue(status.ok)
        assertTrue(status.data.getBoolean("call_idle"))
        assertFalse(status.data.getBoolean("outgoing_ai_running"))
        val takeover = UsbLabControl.execute(app, "call_takeover", JSONObject())
        assertFalse(takeover.ok)
        assertEquals("no_tracked_call", takeover.code)
        val resume = UsbLabControl.execute(app, "call_resume", JSONObject())
        assertFalse(resume.ok)
        assertEquals("no_tracked_call", resume.code)
        val stop = UsbLabControl.execute(app, "call_stop_owned", JSONObject())
        assertFalse(stop.ok)
        assertEquals("no_exact_outgoing_ai_call", stop.code)
        val unknown = UsbLabControl.execute(app, "place_call", JSONObject())
        assertFalse(unknown.ok)
        assertEquals("unknown_action", unknown.code)
    }

    @Test fun configRequiresExplicitVoiceAccountAndRecordingPreference() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val before = UsbLabControl.execute(app, "config_get", JSONObject())
        assertTrue(before.ok)
        val invalid = UsbLabControl.execute(app, "incoming_config",
            JSONObject().put("mode", "AI"))
        assertFalse(invalid.ok)
        assertEquals("explicit_recording_choice_required", invalid.code)
        val invalidAccount = UsbLabControl.execute(app, "incoming_config",
            JSONObject().put("mode", "AI").put("record", false)
                .put("account_index", -1))
        assertFalse(invalidAccount.ok)
        assertEquals("invalid_voice_account_index", invalidAccount.code)
        val after = UsbLabControl.execute(app, "config_get", JSONObject())
        assertTrue(after.ok)
        assertEquals(before.data.getString("incoming_policy"),
            after.data.getString("incoming_policy"))
    }
}
