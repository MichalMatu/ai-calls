package pl.michalmatu.aicallbridge.developerrelay

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisedCallReadinessDiagnosticsTest {
    private fun snapshot(
        phone: Boolean = true,
        shizuku: Boolean = true,
        active: Boolean = true,
        managed: Boolean = true,
        mode: Int? = AudioManager.MODE_IN_CALL,
    ) = SupervisedCallReadinessSnapshot(
        microphonePermission = true,
        phoneStatePermission = phone,
        shizukuReady = shizuku,
        activeCellularCall = active,
        managedCall = managed,
        audioMode = mode,
    )

    @Test fun activeCallWithoutModeInCallIsReportedAndBlocked() {
        val reading = snapshot(mode = AudioManager.MODE_IN_COMMUNICATION)
        assertEquals(SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, reading.failure)
        val report = reading.renderReport()
        assertTrue(report.contains("telecom_in_call=true"))
        assertTrue(report.contains("audio_mode=MODE_IN_COMMUNICATION"))
        assertTrue(report.contains("failure_reason=audio_route_not_ready"))
        assertTrue(report.endsWith("report_complete=true\n"))
    }

    @Test fun noCallCannotBecomeReadyFromAudioModeAlone() {
        val reading = snapshot(active = false)
        assertEquals(SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL, reading.failure)
        assertTrue(reading.renderReport().contains("failure_reason=no_active_cellular_call"))
    }

    @Test fun permissionAndMissingModeFailClosed() {
        assertEquals(SupervisedCallPilotFailure.PERMISSION_REQUIRED, snapshot(phone = false).failure)
        assertEquals(SupervisedCallPilotFailure.SHIZUKU_NOT_READY, snapshot(shizuku = false).failure)
        assertEquals(SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY, snapshot(mode = null).failure)
        assertTrue(snapshot(mode = null).renderReport().contains("audio_mode=UNAVAILABLE"))
    }

    @Test fun reportFieldsAreWhitelistedAndSuccessRequiresEveryGate() {
        val reading = snapshot()
        assertNull(reading.failure)
        val report = reading.renderReport()
        assertTrue(report.contains("failure_reason=none"))
        assertTrue(report.contains("telecom_managed_call=true"))
        assertFalse(report.contains("transcript"))
        assertFalse(report.contains("phone_number"))
        assertEquals(9, report.lineSequence().count { it.isNotEmpty() })
    }
}
