package pl.michalmatu.aicallbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

class RealtimeLiveCallSmokeStateTrackerTest {
    @Test
    fun controlledTakeoverAfterActiveIsPass() {
        val tracker = RealtimeLiveCallSmokeStateTracker()

        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL)))
        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME)))
        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.STARTING_MEDIA)))
        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.ACTIVE)))
        tracker.markControlledStopRequested()
        assertNull(tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.STOPPING)))
        val result = tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.TAKEN_OVER))!!

        assertEquals("PASS", result.status)
        assertEquals("controlled_live_session_completed", result.reason)
        assertEquals(
            listOf(
                CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL,
                CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME,
                CallRealtimeSessionOrchestratorState.STARTING_MEDIA,
                CallRealtimeSessionOrchestratorState.ACTIVE,
                CallRealtimeSessionOrchestratorState.STOPPING,
                CallRealtimeSessionOrchestratorState.TAKEN_OVER,
            ),
            result.states,
        )
        assertTrue(result.render().contains("realtime_live_call_smoke=PASS"))
    }

    @Test
    fun takeoverBeforeActiveFailsClosed() {
        val tracker = RealtimeLiveCallSmokeStateTracker()
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL))
        tracker.markControlledStopRequested()

        val result = tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.TAKEN_OVER))!!

        assertEquals("FAIL", result.status)
        assertEquals("takeover_before_active", result.reason)
    }

    @Test
    fun realtimeOrMediaFailureIsNeverPass() {
        val tracker = RealtimeLiveCallSmokeStateTracker()
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.FETCHING_CREDENTIAL))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME))
        tracker.observe(snapshot(CallRealtimeSessionOrchestratorState.STARTING_MEDIA))

        val result = tracker.observe(
            snapshot(CallRealtimeSessionOrchestratorState.FAILED, "IOException:socket closed"),
        )!!

        assertEquals("FAIL", result.status)
        assertEquals("realtime_live_session_failed", result.reason)
    }

    @Test
    fun timeoutDistinguishesStartupFromControlledCleanup() {
        val startup = RealtimeLiveCallSmokeStateTracker()
        startup.observe(snapshot(CallRealtimeSessionOrchestratorState.CONNECTING_REALTIME))
        assertEquals("startup_timeout", startup.timeout().reason)

        val cleanup = RealtimeLiveCallSmokeStateTracker()
        cleanup.observe(snapshot(CallRealtimeSessionOrchestratorState.ACTIVE))
        cleanup.markControlledStopRequested()
        assertEquals("controlled_stop_timeout", cleanup.timeout().reason)
    }

    @Test
    fun optionalTraceIsRenderedOnOneLine() {
        val result = RealtimeLiveCallSmokeResult(
            status = "PASS",
            reason = "controlled_live_session_completed",
            detail = null,
            states = listOf(CallRealtimeSessionOrchestratorState.TAKEN_OVER),
        ).withTrace("1@0:CONNECT_START\n2@10:CLOSED\r")

        assertEquals("1@0:CONNECT_START 2@10:CLOSED ", result.trace)
        assertTrue(result.render().contains("trace=1@0:CONNECT_START 2@10:CLOSED "))
    }

    private fun snapshot(
        state: CallRealtimeSessionOrchestratorState,
        failure: String? = null,
    ) = CallRealtimeSessionOrchestratorSnapshot(
        generation = 1L,
        state = state,
        mediaGeneration = if (state == CallRealtimeSessionOrchestratorState.ACTIVE) 9L else null,
        failureReason = failure,
    )
}
