package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.app.Instrumentation
import android.app.role.RoleManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.MainActivity
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore

/** Isolated emulator only. Real role dialogs are cancelled without granting the role or starting a call. */
@RunWith(AndroidJUnit4::class)
class AndroidDialerRoleUxTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun isolatedEmulatorOnly() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }

    @Test fun settingsButtonShowsTheRealAndroidRoleDialogAndCancellationReturnsToSettings() {
        verifyRealRoleDialogAndCancellation(SettingsHubActivity::class.java)
    }

    @Test fun homeButtonShowsTheRealAndroidRoleDialogAndCancellationReturnsHome() {
        verifyRealRoleDialogAndCancellation(MainActivity::class.java)
    }

    private fun <A : Activity> verifyRealRoleDialogAndCancellation(activityClass: Class<A>) {
        val role = requireNotNull(context.getSystemService(RoleManager::class.java))
        assertTrue(role.isRoleAvailable(RoleManager.ROLE_DIALER))
        assertFalse(role.isRoleHeld(RoleManager.ROLE_DIALER))
        val controller = requireNotNull(role.createRequestRoleIntent(RoleManager.ROLE_DIALER).`package`)
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        val beforeAudio = AudioEngineProfileStore(context).load()
        val beforeGrant = CampaignAuthorizationStore(context).load()
        val automation = instrumentation.uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            ActivityScenario.launch(activityClass).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<Button>("dialer-role-request").performClick()
                }
                assertTrue("Android must show a real role dialog identifying this app", eventually {
                    val root = automation.rootInActiveWindow
                    root?.packageName?.toString() == controller &&
                        root.findAccessibilityNodeInfosByText(appLabel).isNotEmpty()
                })
                val cancel = requireNotNull(automation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByViewId("android:id/button2")
                    ?.firstOrNull { it.isEnabled && it.isVisibleToUser }) {
                    "The system role dialog must offer explicit cancellation"
                }
                assertTrue(cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertTrue("Cancellation must return to the app", eventually {
                    automation.rootInActiveWindow?.packageName?.toString() == context.packageName
                })
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals(activity.getString(R.string.role_ui_missing),
                        root.findViewWithTag<TextView>("dialer-role-status").text.toString())
                    assertTrue(root.findViewWithTag<Button>("dialer-role-request").isEnabled)
                }
                assertFalse(role.isRoleHeld(RoleManager.ROLE_DIALER))
                assertEquals(beforeAudio, AudioEngineProfileStore(context).load())
                assertEquals(beforeGrant, CampaignAuthorizationStore(context).load())
            }
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
        }
    }

    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    @Test fun homeAndSettingsOnlyShowRoleStatusWithoutLaunchingAPrompt() {
        val role = requireNotNull(context.getSystemService(RoleManager::class.java))
        assumeTrue(role.isRoleAvailable(RoleManager.ROLE_DIALER) == true)
        val monitor = instrumentation.addMonitor(
            IntentFilter(role.createRequestRoleIntent(RoleManager.ROLE_DIALER).action),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        val beforeAudio = AudioEngineProfileStore(context).load()
        val beforeGrant = CampaignAuthorizationStore(context).load()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val card = root.findViewWithTag<View>("dialer-role-card")
                    assertNotNull(card)
                    assertEquals(if (role.isRoleHeld(RoleManager.ROLE_DIALER)) View.GONE else View.VISIBLE,
                        card.visibility)
                }
            }
            ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals(View.VISIBLE, root.findViewWithTag<View>("dialer-role-card").visibility)
                    val status = if (role.isRoleHeld(RoleManager.ROLE_DIALER))
                        R.string.role_ui_ready else R.string.role_ui_missing
                    assertEquals(activity.getString(status),
                        root.findViewWithTag<TextView>("dialer-role-status").text.toString())
                }
            }
            assertEquals(0, monitor.hits)
            assertEquals(beforeAudio, AudioEngineProfileStore(context).load())
            assertEquals(beforeGrant, CampaignAuthorizationStore(context).load())
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun explicitTapOpensOnlyTheSystemRoleChoiceAndCancellationDoesNotRetry() {
        val role = requireNotNull(context.getSystemService(RoleManager::class.java))
        assumeTrue(role.isRoleAvailable(RoleManager.ROLE_DIALER) == true)
        assumeTrue(!role.isRoleHeld(RoleManager.ROLE_DIALER))
        val beforeAudio = AudioEngineProfileStore(context).load()
        val beforeGrant = CampaignAuthorizationStore(context).load()
        val monitor = instrumentation.addMonitor(
            IntentFilter(role.createRequestRoleIntent(RoleManager.ROLE_DIALER).action),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<Button>("dialer-role-request").performClick()
                }
                assertEquals(1, monitor.hits)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals(activity.getString(R.string.role_ui_missing),
                        root.findViewWithTag<TextView>("dialer-role-status").text.toString())
                    assertTrue(root.findViewWithTag<Button>("dialer-role-request").isEnabled)
                }
                assertEquals(1, monitor.hits)
            }
            assertEquals(beforeAudio, AudioEngineProfileStore(context).load())
            assertEquals(beforeGrant, CampaignAuthorizationStore(context).load())
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
