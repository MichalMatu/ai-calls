package pl.michalmatu.aicallbridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.campaign.CampaignRuntimeStatusStore

/** STOP is idempotent on a real idle device and never uses global hangup. */
@RunWith(AndroidJUnit4::class)
class AndroidStopAiIdempotenceContractTest {
    @Test fun stopTwiceOffCallDoesNotDialOrDisconnectAnotherSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED,
        )
        val telecom = context.getSystemService(TelecomManager::class.java)
        assumeTrue(telecom != null && !telecom.isInCall)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(2) {
                scenario.onActivity { activity ->
                    val stop = findStop(activity.window.decorView)
                    requireNotNull(stop) { "stop_ai_button_not_found" }
                    stop.performClick()
                }
            }
        }
        var status = ""
        repeat(40) {
            status = CampaignRuntimeStatusStore(context).load().orEmpty()
            if (status.startsWith("STOP AI:")) return@repeat
            SystemClock.sleep(50)
        }
        assertTrue("STOP must publish a factual result", status.startsWith("STOP AI:"))
        assertFalse("STOP cannot create a cellular call", telecom.isInCall)
    }

    private fun findStop(view: View): Button? {
        if (view is Button && view.tag == "home-stop-ai") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findStop(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
