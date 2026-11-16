package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S8AndroidAudioCapabilitySnapshotTest {
    @Test fun dialerAndMicrophoneNeverImplyGsmPcmAccess() {
        val snapshot = S8AndroidAudioCapabilitySnapshot(
            dialerRoleHeld = true,
            microphoneGranted = true,
            phoneStateGranted = true,
            privilegedCaptureGranted = false,
        )
        val report = snapshot.renderReport()
        assertTrue(report.contains("s8_dialer_role_held=true"))
        assertTrue(report.contains("s8_public_cellular_rx=not_available"))
        assertTrue(report.contains("s8_public_cellular_tx=not_available"))
        assertTrue(report.contains("s8_cellular_pcm_verified=false"))
        assertFalse(report.contains("phone_number"))
    }
}
