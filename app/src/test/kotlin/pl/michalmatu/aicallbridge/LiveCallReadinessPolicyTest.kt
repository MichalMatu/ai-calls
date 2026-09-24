package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCallReadinessPolicyTest {
    @Test
    fun allRequiredCapabilitiesAreReady() {
        val result = LiveCallReadinessPolicy.evaluate(readyInputs())

        assertTrue(result.ready)
        assertEquals(null, result.failure)
        assertTrue(result.renderReport().contains("live_call_readiness=true"))
        assertTrue(result.renderReport().contains("probe_complete=true"))
    }

    @Test
    fun missingMicrophoneFailsBeforeShizukuChecksCanGrantReadiness() {
        val result = LiveCallReadinessPolicy.evaluate(
            readyInputs().copy(recordAudioGranted = false),
        )

        assertFalse(result.ready)
        assertEquals(
            LiveCallReadinessFailure.RECORD_AUDIO_PERMISSION_REQUIRED,
            result.failure,
        )
    }

    @Test
    fun missingBinderUnsupportedRuntimeAndPermissionFailClosed() {
        assertEquals(
            LiveCallReadinessFailure.SHIZUKU_BINDER_UNAVAILABLE,
            LiveCallReadinessPolicy.evaluate(
                readyInputs().copy(
                    shizukuBinderAvailable = false,
                    shizukuSupported = false,
                    shizukuPermissionGranted = false,
                ),
            ).failure,
        )
        assertEquals(
            LiveCallReadinessFailure.SHIZUKU_UNSUPPORTED,
            LiveCallReadinessPolicy.evaluate(
                readyInputs().copy(
                    shizukuSupported = false,
                    shizukuPermissionGranted = false,
                ),
            ).failure,
        )
        assertEquals(
            LiveCallReadinessFailure.SHIZUKU_PERMISSION_REQUIRED,
            LiveCallReadinessPolicy.evaluate(
                readyInputs().copy(shizukuPermissionGranted = false),
            ).failure,
        )
    }

    private fun readyInputs() = LiveCallReadinessInputs(
        recordAudioGranted = true,
        shizukuBinderAvailable = true,
        shizukuSupported = true,
        shizukuPermissionGranted = true,
    )
}
