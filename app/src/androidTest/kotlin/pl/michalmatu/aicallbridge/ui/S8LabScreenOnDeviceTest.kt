package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android emulator only. Tests that S8 can be launched/used WITHOUT ADB
 * at runtime, Shizuku, GSM calls or an operator account.
 *
 * Instrumentation itself uses ADB to execute; normal customer use does not.
 */
@RunWith(AndroidJUnit4::class)
class S8LabScreenOnDeviceTest {
    @Test fun voipLabRunButtonCompletesSixPhaseUdpAudioAndShowsPass() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            app.packageName, Manifest.permission.READ_PHONE_STATE,
        )
        ActivityScenario.launch(S8VoipLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val start = activity.findViewById<android.view.View>(android.R.id.content)
                    .findViewWithTag<Button>("s8-voip-lab-start")
                assertTrue(start.isEnabled)
                start.performClick()
            }

            var completed = false
            var lastStatus = ""
            for (attempt in 0 until 120) {
                scenario.onActivity { activity ->
                    val root = activity.findViewById<android.view.View>(android.R.id.content)
                    val status = root.findViewWithTag<TextView>("s8-voip-lab-status")
                    lastStatus = status.text.toString()
                    if (lastStatus.contains("PASS")) {
                        completed = true
                        val results = root.findViewWithTag<TextView>("s8-voip-lab-results")
                        assertTrue(results.text.toString().contains("72/72"))
                        assertFalse(root.findViewWithTag<Button>("s8-voip-lab-stop").isEnabled)
                    }
                }
                if (completed) break
                Thread.sleep(100L)
            }
            assertTrue("S8 UI failed to complete local RTP: $lastStatus", completed)
        }
    }

    @Test fun sipLabRemainsIdleAndNeverStartsRegistrationWhenOpened() {
        ActivityScenario.launch(S8SipAccountLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.View>(android.R.id.content)
                assertTrue(root.findViewWithTag<Button>("s8-sip-register-once").isEnabled)
                assertFalse(root.findViewWithTag<Button>("s8-sip-register-stop").isEnabled)
                val password = root.findViewWithTag<EditText>("s8-sip-secret-input")
                assertEquals(0, password.text.length)
                val status = root.findViewWithTag<TextView>("s8-sip-registration-status")
                assertTrue(status.text.toString().contains("Nie połączono"))
            }
        }
    }
}
