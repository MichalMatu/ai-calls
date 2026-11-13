package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisedCallPilotPolicyTest {
    private fun check(
        mic: Boolean = true, phone: Boolean = true, shizuku: Boolean = true,
        active: Boolean = true, mode: Boolean = true,
    ) = SupervisedCallPilotPolicy.check(mic, phone, shizuku, active, mode)

    @Test fun stopFencesTheMediaBeforeAuthorizationIsRevoked() {
        val events = mutableListOf<String>()
        SupervisedCallPilotPolicy.stopBeforeRevocation(
            stopAudio = { events += "stop" },
            revoke = { events += "revoke" },
        )
        assertEquals(listOf("stop", "revoke"), events)
    }

    @Test fun stopFailureStillRevokesAuthorization() {
        var revoked = false
        val error = runCatching {
            SupervisedCallPilotPolicy.stopBeforeRevocation(
                stopAudio = { error("probe_stop_failure") },
                revoke = { revoked = true },
            )
        }
        assertTrue(error.isFailure)
        assertTrue(revoked)
    }

    @Test fun validExistingCallCanStartSupervisedScript() {
        assertNull(check())
    }

    @Test fun noCallIsNeverInterpretedAsDialAuthorization() {
        assertEquals(SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL, check(active = false))
    }

    @Test fun transientRouteRetryIsBoundedAndNeverAcceptsMissingCall() {
        assertEquals(true, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, 0L))
        assertEquals(true, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, 2_999L))
        assertEquals(false, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, 3_000L))
        assertEquals(false, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
            SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL, 500L))
        assertEquals(false, SupervisedCallPilotPolicy.shouldAwaitAudioRoute(null, 500L))
    }

    @Test fun audioRouteMustBeAnActualCellularCallRoute() {
        assertEquals(SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, check(mode = false))
    }

    @Test fun noShizukuCannotBeBypassed() {
        assertEquals(SupervisedCallPilotFailure.SHIZUKU_NOT_READY, check(shizuku = false))
    }

    @Test fun microphoneAndPhoneStateMustBeGranted() {
        assertEquals(SupervisedCallPilotFailure.PERMISSION_REQUIRED, check(mic = false))
        assertEquals(SupervisedCallPilotFailure.PERMISSION_REQUIRED, check(phone = false))
    }

    @Test fun absentCallTakesPriorityOverAudioRoute() {
        assertEquals(SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL,
            check(active = false, mode = false))
    }
}
