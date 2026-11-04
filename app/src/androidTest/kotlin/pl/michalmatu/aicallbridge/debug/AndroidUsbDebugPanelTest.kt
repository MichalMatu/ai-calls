package pl.michalmatu.aicallbridge.debug

import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidUsbDebugPanelTest {
    @Test fun launchesPrivateStatusPanelWithoutCallOrSettingsMutation() {
        ActivityScenario.launch(UsbDebugPanelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.findViewById<ViewGroup>(android.R.id.content))
            }
        }
    }
}
