package pl.michalmatu.aicallbridge.ui

import android.app.Activity
import android.app.Instrumentation
import android.app.role.RoleManager
import android.content.IntentFilter
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.MainActivity
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore

/** System role intents are intercepted; no role grant, call, audio, download or profile change. */
@RunWith(AndroidJUnit4::class)
class AndroidDialerRoleUxTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

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
