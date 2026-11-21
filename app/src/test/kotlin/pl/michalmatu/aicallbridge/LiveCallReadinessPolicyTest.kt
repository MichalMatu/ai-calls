package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCallReadinessPolicyTest {
    private fun inputs(host: Boolean = true, mic: Boolean = true) =
        LiveCallReadinessInputs(recordAudioGranted = mic, shellHostAvailable = host)

    @Test fun completePermissionAndHostReadinessAllowsLiveCall() {
        val result = LiveCallReadinessPolicy.evaluate(inputs())
        assertTrue(result.ready)
        assertEquals(null, result.failure)
        assertTrue(result.renderReport().contains("live_call_readiness=true"))
        assertTrue(result.renderReport().contains("probe_complete=true"))
    }

    @Test fun microphonePermissionFailsBeforeHostReadiness() {
        val failure = LiveCallReadinessPolicy.evaluate(inputs(host = false, mic = false))
        assertFalse(failure.ready)
        assertEquals(LiveCallReadinessFailure.RECORD_AUDIO_PERMISSION_REQUIRED, failure.failure)
    }

    @Test fun missingGsmHostCannotBeDowngraded() {
        val blocked = LiveCallReadinessPolicy.evaluate(inputs(host = false))
        assertEquals(LiveCallReadinessFailure.GSM_SHELL_HOST_UNAVAILABLE, blocked.failure)
        assertFalse(blocked.ready)
        assertTrue(blocked.renderReport().contains("media_transport=gsm_shell_adb"))
        assertTrue(blocked.renderReport().contains("gsm_shell_host_available=false"))
        assertFalse(blocked.renderReport().contains("shizuku_binder_available"))
    }
}
