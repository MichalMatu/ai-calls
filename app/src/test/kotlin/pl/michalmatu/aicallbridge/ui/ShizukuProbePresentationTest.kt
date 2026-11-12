package pl.michalmatu.aicallbridge.ui

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.ui.ShizukuProbePresentation.Outcome

class ShizukuProbePresentationTest {
    private val successful = """probe=shizuku-user-service-v1
mode=offcall-prepare-abort
binder_ping=true
service_uid=2000
prepared_before=false
active_before=false
prepared_after_prepare=true
active_after_prepare=false
heartbeat_after_prepare=false
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
privileged_uid=true
offcall_parity_ok=true"""

    @Test fun expectedInactiveFlagsAreSuccessfulOffCallEvidence() {
        val result = ShizukuProbePresentation.parse(successful)
        assertEquals(Outcome.PASSED, result.outcome)
        assertEquals(true, result.lifecycle)
        assertEquals(Outcome.PASSED, ShizukuProbePresentation.parse(successful.replace("service_uid=2000", "service_uid=0")).outcome)
    }

    @Test fun successFlagCannotHideContradictingLifecycleOrIdentity() {
        for ((before, after) in listOf("prepared_after_abort=false" to "prepared_after_abort=true",
            "active_after_prepare=false" to "active_after_prepare=true", "service_uid=2000" to "service_uid=10748",
            "binder_ping=true" to "binder_ping=false", "offcall_parity_ok=true" to "offcall_parity_ok=false")) {
            assertEquals(before, Outcome.FAILED, ShizukuProbePresentation.parse(successful.replace(before, after)).outcome)
        }
    }

    @Test fun missingUnknownAndDuplicateValuesCannotBecomePass() {
        assertEquals(Outcome.INCOMPLETE, ShizukuProbePresentation.parse("offcall_parity_ok=true").outcome)
        assertEquals(Outcome.INCOMPLETE, ShizukuProbePresentation.parse(successful.replace("prepared_after_abort=false", "")).outcome)
        assertEquals(Outcome.INCOMPLETE, ShizukuProbePresentation.parse(successful.replace("binder_ping=true", "binder_ping=maybe")).outcome)
        assertEquals(Outcome.INCOMPLETE, ShizukuProbePresentation.parse(successful + "\nbinder_ping=true").outcome)
        assertEquals(Outcome.INCOMPLETE, ShizukuProbePresentation.parse(successful.replace("offcall-prepare-abort", "live")).outcome)
    }

    @Test fun explicitFailureRemainsFailureEvenBesideSuccessFlag() {
        assertEquals(Outcome.FAILED, ShizukuProbePresentation.parse(successful + "\nerror=service_disconnected").outcome)
        assertEquals(Outcome.FAILED, ShizukuProbePresentation.parse("error=connect_timeout").outcome)
    }
}
