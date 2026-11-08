package pl.michalmatu.aicallbridge.conversation

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.MainActivity
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import java.io.File

/**
 * Process-local synthetic feed. No Telecom connection, call grant, microphone,
 * audio, model generation, private identity values, or network activity.
 */
@RunWith(AndroidJUnit4::class)
class AndroidUx11FloatingSyntheticTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    private fun checkOverlay(activityClass: Class<out android.app.Activity>, capture: Boolean) {
        assumeFalse("refuse synthetic feed during an actual active session",
            ConversationChatFeed.snapshot().active)
        ActivityScenario.launch(activityClass).use { scene ->
            var token: Long? = null
            try {
                val begin = ConversationChatFeed.begin()
                token = begin
                Thread.sleep(720)
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                        "conversation-floating-button")).visibility)
                }
                ConversationChatFeed.heard(begin, 1, "Synthetic local test: message received.")
                Thread.sleep(720)
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    val icon = requireNotNull(root.findViewWithTag<View>("conversation-floating-button"))
                    assertEquals(View.VISIBLE, icon.visibility)
                    assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                        "conversation-floating-panel")).visibility)
                    assertTrue(icon.performClick())
                    val panel = requireNotNull(root.findViewWithTag<View>("conversation-floating-panel"))
                    assertTrue(panel.isShown)
                }
                // GONE -> VISIBLE requests a fresh layout; measure after the UI frame,
                // not in the same main-thread callback as performClick().
                instrument.waitForIdleSync()
                Thread.sleep(260)
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    val panel = requireNotNull(root.findViewWithTag<View>("conversation-floating-panel"))
                    val host = requireNotNull(root.findViewWithTag<View>(
                        if (capture) "home-conversation-floating-overlay" else
                            "campaign-conversation-floating-overlay"))
                    assertTrue("panel_geometry=${panel.height}/${host.height}",
                        panel.height > host.height * 0.75)
                    assertTrue("panel_geometry=${panel.height}/${host.height}",
                        panel.height < host.height * 0.90)
                }
                ConversationChatFeed.replied(begin, 1,
                    "Synthetic local test: assistant response.")
                Thread.sleep(720)
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    val scroller = requireNotNull(root.findViewWithTag<View>("live-chat-scroll"))
                    assertTrue(scroller.isShown)
                    assertTrue(ConversationChatFeed.snapshot().lines.size == 2)
                }
                if (capture) {
                    // The screenshot is captured from the test thread, never the app main thread.
                    val privateDir = requireNotNull(instrument.targetContext.getExternalFilesDir(
                        "ui-audit-20261107-sprint6"))
                    assertTrue(privateDir.isDirectory || privateDir.mkdirs())
                    val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
                    try {
                        File(privateDir, "chat-active-synthetic.jpg").outputStream().use { stream ->
                            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, stream))
                        }
                    } finally { bitmap.recycle() }
                }
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    assertTrue(requireNotNull(root.findViewWithTag<View>(
                        "live-chat-toggle")).performClick())
                    assertTrue(requireNotNull(root.findViewWithTag<View>(
                        "conversation-floating-button")).isShown)
                }
                ConversationChatFeed.finish(begin, "conversation_completed")
                Thread.sleep(720)
                scene.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                        "conversation-floating-button")).visibility)
                    assertEquals(View.GONE, requireNotNull(root.findViewWithTag<View>(
                        "conversation-floating-panel")).visibility)
                }
            } finally {
                token?.let { ConversationChatFeed.finish(it, "operator_stopped") }
                ConversationChatFeed.clearVisibleHistory()
            }
        }
    }

    @Test fun homeFloatingChatOpensAndDisappearsWithSyntheticMessages() =
        checkOverlay(MainActivity::class.java, capture = true)

    @Test fun campaignFloatingChatUsesTheSameLiveComponent() =
        checkOverlay(CampaignToolActivity::class.java, capture = false)
}
