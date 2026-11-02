package pl.michalmatu.aicallbridge.campaign

import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No phone call or role mutation. Checks the OS-visible exact-call controller prerequisites. */
@RunWith(AndroidJUnit4::class)
class AndroidExactCallRoleReadinessContractTest {
    @Test fun roleGatedControllerAndDialerEntryExist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val candidates = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_DIAL).setPackage(context.packageName),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        assertTrue(
            "the default dialer candidate needs an ACTION_DIAL activity",
            candidates.any { it.activityInfo.name.endsWith("AndroidCallControlsActivity") },
        )
        val controller = CampaignCallControllerRegistry.available(context)
        assertEquals(
            "never acquire an owned-call controller without Android's actual dialer role",
            AndroidTelecomOwnedCallController.canControl(context),
            controller != null,
        )
        val telecom = context.getSystemService(TelecomManager::class.java)
        assertTrue("telecom service must exist", telecom != null)
    }
}
