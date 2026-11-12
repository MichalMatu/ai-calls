package pl.michalmatu.aicallbridge

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity

@RunWith(AndroidJUnit4::class)
class AndroidUx07ParsedUsbStatusTest {
    @Test fun allNonDuplicateStatusesLiveInOneAlwaysVisibleCard() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                val root = activity.window.decorView
                val card = requireNotNull(root.findViewWithTag<View>("usb-status-summary"))
                val keys = listOf("phone", "campaign_service_inactive", "pending_retry",
                    "input", "dialogue", "output", "voice", "profile_valid",
                    "incoming", "incoming_status", "recording", "grant_present",
                    "grant_revoked", "private_evidence_sessions")
                for (key in keys) {
                    val value = requireNotNull(root.findViewWithTag<TextView>("usb-value:" + key))
                    assertNotNull("missing_field:" + key, value)
                    assertTrue("outside_card:" + key, isDescendant(value, card))
                    assertTrue("empty_field:" + key, value.text.isNotBlank())
                }
                assertEquals(keys.size, keys.toSet().size)
                assertNull(root.findViewWithTag<View>("usb-value:recording-current"))
                assertNull(root.findViewWithTag<View>("usb-copy-json"))
                assertNull(root.findViewWithTag<View>("ui-accordion-content:usb-raw"))
                assertNull(root.findViewWithTag<View>("ui-accordion-content:usb-technical"))
                assertNull(root.findViewWithTag<View>("ui-accordion-toggle:usb-technical"))
            }
        }
    }

    private fun isDescendant(view: View, ancestor: View): Boolean {
        var node: View? = view
        while (node != null) {
            if (node === ancestor) return true
            node = node.parent as? View
        }
        return false
    }
}
