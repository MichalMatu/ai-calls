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
    @Test fun primaryStatusHasNoDuplicatesAndTechnicalDetailsAreOptional() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("usb-status-summary"))
                for (key in listOf("phone", "input", "dialogue", "output", "recording", "incoming")) {
                    assertNotNull("missing_field:" + key, root.findViewWithTag<View>("usb-value:" + key))
                }
                assertNull(root.findViewWithTag<View>("usb-value:campaign"))
                assertNull(root.findViewWithTag<View>("usb-value:recording-current"))
                assertNull(root.findViewWithTag<View>("usb-copy-json"))
                assertNull(root.findViewWithTag<View>("ui-accordion-content:usb-raw"))
                val details = requireNotNull(root.findViewWithTag<View>(
                    "ui-accordion-content:usb-technical"))
                assertEquals(View.GONE, details.visibility)
                requireNotNull(root.findViewWithTag<View>(
                    "ui-accordion-toggle:usb-technical")).performClick()
                assertEquals(View.VISIBLE, details.visibility)
            }
        }
    }
}
