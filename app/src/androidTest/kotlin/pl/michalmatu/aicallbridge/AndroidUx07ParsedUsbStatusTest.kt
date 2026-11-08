package pl.michalmatu.aicallbridge

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity

@RunWith(AndroidJUnit4::class)
class AndroidUx07ParsedUsbStatusTest {
    @Test fun sourceFieldsAreVisibleAndRawJsonIsCollapsible() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("usb-status-summary"))
                for (key in listOf("phone", "campaign", "input", "dialogue", "output", "recording", "incoming")) {
                    assertNotNull("missing_field:" + key, root.findViewWithTag<View>("usb-value:" + key))
                }
                assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                    "ui-accordion-content:usb-raw")).visibility)
            }
        }
    }
}
