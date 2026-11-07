package pl.michalmatu.aicallbridge

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

/**
 * Safe non-mutating expanded states alongside the base 15-screen audit.
 * Files remain in app-specific storage and are pulled ONLY to owner's private Mac folder.
 */
@RunWith(AndroidJUnit4::class)
class AndroidVisualPolishAuditTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    private fun findPrefix(view: View, prefix: String): View? {
        if ((view.tag as? String)?.startsWith(prefix) == true) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findPrefix(view.getChildAt(i), prefix)?.let { return it }
        }
        return null
    }

    private fun <T : Activity> snapshot(type: Class<T>, label: String, expand: (T) -> Unit) {
        ActivityScenario.launch(type).use { scenario ->
            instrument.waitForIdleSync()
            var folder: File? = null
            scenario.onActivity { activity ->
                folder = requireNotNull(activity.getExternalFilesDir("ui-audit-20261107-sprint3"))
                requireNotNull(folder).mkdirs()
                expand(activity)
            }
            instrument.waitForIdleSync()
            if (label == "campaigns-evidence-expanded") {
                scenario.onActivity { activity ->
                    val expanded = requireNotNull(activity.window.decorView.findViewWithTag<View>(
                        "ui-accordion-content:evidence"))
                    assertEquals("campaign_section_not_expanded", View.VISIBLE, expanded.visibility)
                    expanded.requestRectangleOnScreen(Rect(0, 0,
                        expanded.width.coerceAtLeast(1), expanded.height.coerceAtLeast(1)), true)
                }
                instrument.waitForIdleSync()
                scenario.onActivity { activity ->
                    val expanded = requireNotNull(activity.window.decorView.findViewWithTag<View>(
                        "ui-accordion-content:evidence"))
                    val visible = Rect()
                    assertTrue("campaign_expansion_outside_viewport", expanded.getGlobalVisibleRect(visible))
                    assertTrue("campaign_expansion_too_small", visible.height() >= 16)
                }
            }
            Thread.sleep(350)
            val image = instrument.uiAutomation.takeScreenshot()
            assertNotNull("audit_capture_missing:" + label, image)
            try {
                val file = File(requireNotNull(folder), label + ".jpg")
                file.outputStream().use { output ->
                    assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 82, output))
                }
                assertTrue("audit_capture_empty:" + label, file.length() > 2000)
                println("UI_AUDIT_EXPANDED=" + label + ";captured=1")
            } finally {
                image.recycle()
            }
        }
    }

    @Test fun languageDropdownOpen() =
        snapshot(SettingsHubActivity::class.java, "settings-language-dropdown") { activity ->
            requireNotNull(activity.window.decorView.findViewWithTag<Spinner>(
                "ui-language-picker")).performClick()
        }

    @Test fun preparationDirectionDropdownOpen() =
        snapshot(ConversationPreparationActivity::class.java, "preparation-direction-dropdown") { activity ->
            requireNotNull(activity.window.decorView.findViewWithTag<Spinner>(
                "preparation-direction-picker")).performClick()
        }

    @Test fun modelTechnicalDetailsExpanded() =
        snapshot(LanguageModelActivity::class.java, "models-technical-expanded") { activity ->
            requireNotNull(findPrefix(activity.window.decorView, "model-details-toggle:")).performClick()
        }

    @Test fun campaignEvidenceExpanded() =
        snapshot(CampaignToolActivity::class.java, "campaigns-evidence-expanded") { activity ->
            requireNotNull(activity.window.decorView.findViewWithTag<View>(
                "ui-accordion-toggle:evidence")).performClick()
        }

    @Test fun engineUnavailableCatalogExpanded() =
        snapshot(AudioEngineSettingsActivity::class.java, "engines-unavailable-expanded") { activity ->
            requireNotNull(findPrefix(activity.window.decorView, "engine-unavailable:")).performClick()
        }

    @Test fun skillsPresetDropdownOpen() =
        snapshot(ConversationSkillsActivity::class.java, "skills-preset-dropdown") { activity ->
            requireNotNull(activity.window.decorView.findViewWithTag<Spinner>(
                "skills-preset-picker")).performClick()
        }
}
