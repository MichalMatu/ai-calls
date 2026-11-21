package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisedCallPilotPolicyTest {
    private fun check(
        mic: Boolean = true, phone: Boolean = true, host: Boolean = true,
        active: Boolean = true, mode: Boolean = true,
    ) = SupervisedCallPilotPolicy.check(mic, phone, host, active, mode)

    @Test fun missingOwnGsmHostFailsClosed() {
        assertEquals(SupervisedCallPilotFailure.GSM_SHELL_NOT_READY, check(host = false))
        assertNull(check())
    }

    @Test fun stopAlwaysFencesBeforeRevocation() {
        val events = mutableListOf<String>()
        SupervisedCallPilotPolicy.stopBeforeRevocation(
            stopAudio = { events += "stop" },
            revoke = { events += "revoke" },
        )
        assertEquals(listOf("stop", "revoke"), events)
    }

    @Test fun stopFailureStillRevokes() {
        var revoked = false
        val result = runCatching {
            SupervisedCallPilotPolicy.stopBeforeRevocation(
                stopAudio = { error("test_stop_failure") },
                revoke = { revoked = true },
            )
        }
        assertTrue(result.isFailure)
        assertTrue(revoked)
    }

    @Test fun noCallIsNotDialAuthorization() {
        assertEquals(SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL, check(active = false))
    }

    @Test fun audioRouteRetryIsBounded() {
        assertTrue(SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, 2_999L))
        assertEquals(false, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, 3_000L))
        assertEquals(false, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL, 500L))
    }

    @Test fun microphonePhoneAndAudioRouteRemainMandatory() {
        assertEquals(SupervisedCallPilotFailure.PERMISSION_REQUIRED, check(mic = false))
        assertEquals(SupervisedCallPilotFailure.PERMISSION_REQUIRED, check(phone = false))
        assertEquals(SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, check(mode = false))
        assertEquals(SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL,
            check(active = false, mode = false))
    }
}
