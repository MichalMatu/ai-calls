package pl.michalmatu.aicallbridge

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity
import pl.michalmatu.aicallbridge.debug.UsbDebugPanelActivity
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity
import pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity
import pl.michalmatu.aicallbridge.textagent.LanguageModelActivity
import pl.michalmatu.aicallbridge.tools.ConversationToolsActivity
import pl.michalmatu.aicallbridge.ui.DeveloperToolsActivity
import pl.michalmatu.aicallbridge.ui.GeneralSettingsActivity
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

/**
 * Read-only physical-screen review. Opens safe pages and scrolls them.
 * Never presses an authorization, save, record, delete, transfer, dial or STOP action.
 * Screenshots stay in local app-specific storage; pull only into owner's private Mac folder.
 * Never emit screenshot pixels, visible text, user identifiers or transcript data to logs/Git.
 */
@RunWith(AndroidJUnit4::class)
class AndroidVisualScreenAuditTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    private fun firstVisibleScroll(view: View): ScrollView? {
        if (view is ScrollView && view.isShown && view.height > 0) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            firstVisibleScroll(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private data class Geometry(val actions: Int, val cramped: Int, val small: Int)

    private fun countGeometry(root: View, density: Float): Geometry {
        var actions = 0
        var cramped = 0
        var small = 0
        fun eligible(view: View): Boolean =
            (view is Button && view !is CompoundButton) || view is ImageButton
        fun rect(view: View): Rect? = Rect().takeIf { view.getGlobalVisibleRect(it) && it.width() > 0 && it.height() > 0 }
        fun visit(view: View) {
            if (eligible(view) && view.isShown) {
                rect(view)?.let {
                    actions++
                    if (it.width() < 46 * density || it.height() < 46 * density) small++
                }
            }
            if (view !is ViewGroup) return
            val direct = (0 until view.childCount).map(view::getChildAt)
                .filter { eligible(it) && it.isShown }
            for (index in 0 until direct.size - 1) {
                val one = rect(direct[index]) ?: continue
                val two = rect(direct[index + 1]) ?: continue
                if (view is android.widget.LinearLayout &&
                    view.orientation == android.widget.LinearLayout.VERTICAL) {
                    val gap = two.top - one.bottom
                    if (gap in 0 until (8 * density).toInt()) cramped++
                } else if (view is android.widget.LinearLayout) {
                    val gap = two.left - one.right
                    if (gap in 0 until (8 * density).toInt()) cramped++
                }
            }
            for (child in direct) { /* owned by normal recursive pass below */ }
            for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
        return Geometry(actions, cramped, small)
    }

    private fun <T : Activity> capture(type: Class<T>, label: String) {
        ActivityScenario.launch(type).use { scenario ->
            Thread.sleep(450)
            instrument.waitForIdleSync()
            var scrollRange = 0
            var privateDir: File? = null
            var density = 1f
            scenario.onActivity { activity ->
                val folder = requireNotNull(activity.getExternalFilesDir("ui-audit-20261107-sprint4"))
                assertTrue("audit_dir_unavailable:" + label, folder.isDirectory || folder.mkdirs())
                privateDir = folder
                density = activity.resources.displayMetrics.density
                val scroll = firstVisibleScroll(activity.window.decorView)
                scrollRange = if (scroll == null) 0 else
                    ((scroll.getChildAt(0)?.height ?: 0) - scroll.height).coerceAtLeast(0)
            }
            val offsets = listOf(0, scrollRange / 2, scrollRange).distinct()
            var screenshotCount = 0
            var maxCramped = 0
            var maxSmall = 0
            var maxActions = 0
            for ((index, offset) in offsets.withIndex()) {
                scenario.onActivity { activity ->
                    firstVisibleScroll(activity.window.decorView)?.scrollTo(0, offset)
                }
                instrument.waitForIdleSync()
                Thread.sleep(160)
                scenario.onActivity { activity ->
                    val geometry = countGeometry(activity.window.decorView, density)
                    maxCramped = maxOf(maxCramped, geometry.cramped)
                    maxSmall = maxOf(maxSmall, geometry.small)
                    maxActions = maxOf(maxActions, geometry.actions)
                }
                val screenshot = instrument.uiAutomation.takeScreenshot()
                assertNotNull("screenshot_blocked:" + label, screenshot)
                val bitmap = checkNotNull(screenshot)
                try {
                    assertTrue("invalid_capture_width:" + label, bitmap.width > 200)
                    val file = File(requireNotNull(privateDir), label + "-" + index + ".jpg")
                    file.outputStream().use { output ->
                        assertTrue("screenshot_encode_failed:" + label,
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
                    }
                    screenshotCount++
                } finally {
                    bitmap.recycle()
                }
            }
            println("UI_AUDIT=$label;pages=$screenshotCount;actions=$maxActions;cramped=$maxCramped;small=$maxSmall")
        }
    }

    @Test fun home() = capture(MainActivity::class.java, "home")
    @Test fun calls() = capture(ConversationHistoryActivity::class.java, "calls")
    @Test fun lab() = capture(ConversationSkillLabActivity::class.java, "lab")
    @Test fun settings() = capture(SettingsHubActivity::class.java, "settings")
    @Test fun generalSettings() = capture(GeneralSettingsActivity::class.java, "general-settings")
    @Test fun engines() = capture(AudioEngineSettingsActivity::class.java, "engines")
    @Test fun languageModels() = capture(LanguageModelActivity::class.java, "models")
    @Test fun incomingAssistant() = capture(IncomingAssistantSettingsActivity::class.java, "incoming")
    @Test fun preparation() = capture(ConversationPreparationActivity::class.java, "preparation")
    @Test fun skills() = capture(ConversationSkillsActivity::class.java, "skills")
    @Test fun conversationTools() = capture(ConversationToolsActivity::class.java, "tools")
    @Test fun developer() = capture(DeveloperToolsActivity::class.java, "developer")
    @Test fun usbDiagnostics() = capture(UsbDebugPanelActivity::class.java, "usb-diagnostics")
    @Test fun phoneEnrollment() = capture(PhoneEnrollmentActivity::class.java, "phone-enrollment")
    @Test fun campaigns() = capture(CampaignToolActivity::class.java, "campaigns")
}
