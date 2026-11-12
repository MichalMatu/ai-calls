package pl.michalmatu.aicallbridge.ui

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R

/** Read-only layout contract; never executes calls or requests grants. */
@RunWith(AndroidJUnit4::class)
class AndroidCapabilityDiagnosticsLayoutTest {
    @Test fun capabilityReportHasItsOwnCardNotAnAccordionOrRawFooter() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        ActivityScenario.launch(CapabilityDiagnosticsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("capabilities-card"))
                assertNotNull(root.findViewWithTag<View>("capabilities-probe-status"))
                assertNotNull(root.findViewWithTag<View>("capabilities-routes"))
                for (key in listOf("model","android","audio_mode","permission_record_audio",
                    "audio_source_MIC","audio_source_VOICE_COMMUNICATION",
                    "audio_source_VOICE_RECOGNITION","audio_source_VOICE_CALL",
                    "audio_source_VOICE_DOWNLINK","audio_source_VOICE_UPLINK")) {
                    assertNotNull("missing:"+key,
                        root.findViewWithTag<TextView>("capabilities-value:"+key))
                }
                assertNull(root.findViewWithTag<View>("ui-accordion-toggle:capability"))
            }
        }
    }
}
