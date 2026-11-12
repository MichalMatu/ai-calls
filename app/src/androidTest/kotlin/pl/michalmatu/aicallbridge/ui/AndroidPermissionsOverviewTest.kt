package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only on isolated emulator; no system permission prompt or real call. */
@RunWith(AndroidJUnit4::class)
class AndroidPermissionsOverviewTest {
    @Before fun isolatedEmulator() { assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish")) }

    @Test fun permissionsOverviewExposesRoleMicAndSimWithoutRequestingGrants() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(GeneralSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                for (tag in listOf("dialer-role-card", "permissions-microphone",
                    "permissions-phone-state", "permissions-microphone-status",
                    "permissions-phone-state-status"))
                    assertNotNull(tag, root.findViewWithTag<View>(tag))
                val micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                val phoneGranted = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED
                assertEquals(if (micGranted) View.GONE else View.VISIBLE,
                    root.findViewWithTag<View>("permissions-microphone-request").visibility)
                assertEquals(if (phoneGranted) View.GONE else View.VISIBLE,
                    root.findViewWithTag<View>("permissions-phone-state-request").visibility)
            }
        }
    }
}
