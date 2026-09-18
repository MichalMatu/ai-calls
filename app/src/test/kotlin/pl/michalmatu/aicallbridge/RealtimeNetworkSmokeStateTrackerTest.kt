package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

class RealtimeNetworkSmokeStateTrackerTest {
    @Test
    fun expectedOffCallFailureAfterRealtimeConnectIsPass() {
        val tracker = RealtimeNetworkSmokeStateTracker()

        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL)))
        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME)))
        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.STARTING_MEDIA)))
        val result = tracker.observe(
            snapshot(
                CallRealtimeSessionOrchestratorState.FAILED,
                "IllegalStateException:cellular call is not active",
            ),
        )!!

        assertEquals("PASS", result.status)
        assertEquals("realtime_connected_off_call_media_rejected", result.reason)
        assertEquals(
            listOf(
                CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL,
                CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME,
                CallRealtimeSessionOrchestratorState.STARTING_MEDIA,
                CallRealtimeSessionOrchestratorState.FAILED,
            ),
            result.states,
        )
        assertTrue(result.render().contains("FETCHING_CREDENTIAL>CONNECTING_REALTIME>STARTING_MEDIA>FAILED"))
    }

    @Test
    fun failureBeforeStartingMediaIsRealtimeConnectionFailure() {
        val tracker = RealtimeNetworkSmokeStateTracker()
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME))

        val result = tracker.observe(
            snapshot(CallRealtimeSessionOrchestratorState.FAILED, "IOException:credential backend returned HTTP 502"),
        )!!

        assertEquals("FAIL", result.status)
        assertEquals("realtime_connect_failed", result.reason)
    }

    @Test
    fun activeOffCallIsImmediateSafetyFailure() {
        val tracker = RealtimeNetworkSmokeStateTracker()
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.STARTING_MEDIA))

        val result = tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.ACTIVE))!!

        assertEquals("FAIL", result.status)
        assertEquals("unexpected_active_off_call", result.reason)
    }

    @Test
    fun unexpectedMediaFailureAfterRealtimeConnectIsNotPass() {
        val tracker = RealtimeNetworkSmokeStateTracker()
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.STARTING_MEDIA))

        val result = tracker.observe(
            snapshot(CallRealtimeSessionOrchestratorState.FAILED, "IllegalStateException:unexpected route failure"),
        )!!

        assertEquals("FAIL", result.status)
        assertEquals("unexpected_off_call_failure", result.reason)
    }

    @Test
    fun optionalTraceIsRenderedOnOneSanitizedLine() {
        val result = RealtimeNetworkSmokeResult(
            status = "PASS",
            reason = "realtime_connected_off_call_media_rejected",
            detail = null,
            states = listOf(CallRealtimeSessionOrchestratorState.FAILED),
        ).withTrace("1@0:CONNECT_START\n2@10:CONNECT_SUCCESS\r3@11:CLOSED")

        assertEquals(
            "1@0:CONNECT_START 2@10:CONNECT_SUCCESS 3@11:CLOSED",
            result.trace,
        )
        assertTrue(result.render().contains("trace=1@0:CONNECT_START 2@10:CONNECT_SUCCESS 3@11:CLOSED"))
    }

    private fun snapshot(
        state: CallRealtimeSessionOrchestratorState,
        failure: String? = null,
    ) = CallRealtimeSessionOrchestratorSnapshot(
        generation = 1L,
        state = state,
        mediaGeneration = null,
        failureReason = failure,
    )
}
