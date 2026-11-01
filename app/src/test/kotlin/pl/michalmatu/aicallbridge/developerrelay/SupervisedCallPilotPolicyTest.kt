package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupervisedCallPilotPolicyTest {
    private fun check(
        mic: Boolean = true, phone: Boolean = true, shizuku: Boolean = true,
        active: Boolean = true, mode: Boolean = true,
    ) = SupervisedCallPilotPolicy.check(mic, phone, shizuku, active, mode)

    @Test fun validExistingCallCanStartSupervisedScript() {
        assertNull(check())
    }

    @Test fun noCallIsNeverInterpretedAsDialAuthorization() {
        assertEquals(SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL, check(active = false))
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
