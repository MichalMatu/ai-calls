package pl.michalmatu.aicallbridge

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity
import pl.michalmatu.aicallbridge.campaign.AppointmentMvpActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity

/** Only launches pages. Does not edit settings, start download or make calls. */
@RunWith(AndroidJUnit4::class)
class AndroidUx08NavigationCleanupTest {
    @Test fun incomingKeepsItsPolicyEditorWithoutDuplicatedAgentModelShortcuts() {
        ActivityScenario.launch(IncomingAssistantSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                assertNotNull(root.findViewWithTag<View>("incoming-save-mode"))
                assertTrue(requireNotNull(root.findViewWithTag<View>("app-tab:settings")).isSelected)
                fun labels(view: View): List<String> = buildList {
                    if (view is TextView) add(view.text.toString())
                    if (view is ViewGroup) repeat(view.childCount) {
                        addAll(labels(view.getChildAt(it)))
                    }
                }
                val visible = labels(root)
                assertTrue(visible.contains(a.getString(R.string.incoming_ui_record_hint)))
                assertFalse(visible.any { it.contains("Skills,") || it.contains("scenarios, and data:") })
                assertFalse(visible.any { it.contains("Zmiana trybu dotyczy") ||
                    it.contains("Mode changes apply") })
                assertFalse(visible.any { it.contains("Aktywnej rozmowy nie zmieniono") ||
                    it.contains("Active calls are unchanged") })
            }
        }
    }

    @Test fun appointmentDoesNotExposeObsoleteCampaignSetup() {
        ActivityScenario.launch(AppointmentMvpActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("appointment-authorize-call"))
                assertNotNull(root.findViewWithTag<View>("appointment-review-result"))
                assertNull(root.findViewWithTag<View>("campaign-primary-actions"))
                assertNull(root.findViewWithTag<View>("campaign-supervised-card"))
            }
        }
    }

    @Test fun modelsExposeExplicitOfflineVerificationWithoutStartingTransfers() {
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                val action = requireNotNull(root.findViewWithTag<View>("model-manage-files"))
                assertTrue(action.isClickable)
                assertNotNull(root.findViewWithTag<View>("model-voice-compatibility-check"))
                assertNull(root.findViewWithTag<View>("parakeet-transfer-download"))
            }
        }
    }

    @Test fun usbSeparatesRecordingPolicyFromActualCallState() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val root = a.window.decorView
                assertNotNull(root.findViewWithTag<View>("usb-value:recording"))
                assertNull(root.findViewWithTag<View>("usb-value:recording-current"))
            }
        }
    }
}
