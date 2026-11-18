package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.session.CallMediaBackendMode

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

    @Test
    fun s8ShellHostReplacesShizukuAndCannotFallback() {
        val noShell = readyInputs().copy(
            mode = CallMediaBackendMode.GSM_SHELL_ADB,
            shizukuBinderAvailable = true,
            shizukuSupported = true,
            shizukuPermissionGranted = true,
            shellHostAvailable = false,
        )
        val blocked = LiveCallReadinessPolicy.evaluate(noShell)
        assertEquals(LiveCallReadinessFailure.GSM_SHELL_HOST_UNAVAILABLE, blocked.failure)
        assertFalse(blocked.ready)
        assertTrue(blocked.renderReport().contains("media_transport=gsm_shell_adb"))
        assertFalse(blocked.renderReport().contains("shizuku_binder_available"))

        val ready = LiveCallReadinessPolicy.evaluate(noShell.copy(
            shizukuBinderAvailable = false,
            shizukuSupported = false,
            shizukuPermissionGranted = false,
            shellHostAvailable = true,
        ))
        assertTrue(ready.ready)
    }

    private fun readyInputs() = LiveCallReadinessInputs(
        recordAudioGranted = true,
        shizukuBinderAvailable = true,
        shizukuSupported = true,
        shizukuPermissionGranted = true,
    )
}
