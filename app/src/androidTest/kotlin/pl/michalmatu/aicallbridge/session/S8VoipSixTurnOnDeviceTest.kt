package pl.michalmatu.aicallbridge.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator-only native Android UDP/PCM confirmation. No Telecom, no microphone,
 * no Shizuku, no public network, no SIP credentials and no audio files.
 * Never execute instrumentation against the owner's real Samsung device.
 */
@RunWith(AndroidJUnit4::class)
class S8VoipSixTurnOnDeviceTest {
    @Test fun sixPhasesRunWithRealAndroidUdpAndCoordinatorStop() {
        S8VoipSixTurnLab().use { lab ->
            val result = lab.run()
            assertEquals(6, result.phasesCompleted())
            assertEquals(72, result.rxFrames())
            assertEquals(72, result.txFrames())
            assertTrue(result.sdp())
            assertTrue(result.publicEndpointRejected())
            assertTrue(result.stopped())
            assertTrue(result.passed())
        }
    }
}
