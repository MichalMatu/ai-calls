package pl.michalmatu.aicallbridge.agent

import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot
import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorState

class CallRealtimeAgentRuntimeTest {
    @Test
    fun runtimeDelegatesSessionSurfaceAndClosesInSafetyOrderExactlyOnce() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val session = FakeSession(events)
        val executor = RecordingExecutor(events)
        val mediaOwner = AutoCloseable { events += "media" }
        val runtime = CallRealtimeAgentRuntime(session, executor, mediaOwner)

        assertEquals(7L, runtime.start())
        assertEquals(CallRealtimeSessionOrchestratorState.ACTIVE, runtime.snapshot().state)
        assertTrue(runtime.hasPendingUserDecision())
        assertTrue(runtime.approvePendingProposal().isSuccess)
        assertTrue(runtime.rejectPendingProposal().isSuccess)
        runtime.takeOverNow()
        assertEquals(1, session.takeOverCalls)

        runtime.close()
        runtime.close()

        assertEquals(listOf("controller", "executor", "media"), events)
        assertTrue(executor.isShutdown)
        assertEquals(1, session.closeCalls)
        assertFails<IllegalStateException> { runtime.start() }
    }

    private class FakeSession(
        private val events: MutableList<String>,
    ) : CallRealtimeAgentSession {
        var takeOverCalls = 0
        var closeCalls = 0

        override fun start(): Long = 7L

        override fun snapshot(): CallRealtimeSessionOrchestratorSnapshot =
            CallRealtimeSessionOrchestratorSnapshot(
                generation = 7L,
                state = CallRealtimeSessionOrchestratorState.ACTIVE,
                mediaGeneration = 11L,
                failureReason = null,
            )

        override fun takeOverNow() {
            takeOverCalls++
        }

        override fun hasPendingUserDecision(): Boolean = true

        override fun approvePendingProposal(): Result<CallProposal> = Result.success(proposal())

        override fun rejectPendingProposal(): Result<CallProposal> = Result.success(proposal())

        override fun close() {
            closeCalls++
            events += "controller"
        }

        private fun proposal() = CallProposal(null, null, null, "Clinic", null)
    }

    private class RecordingExecutor(
        private val events: MutableList<String>,
    ) : AbstractExecutorService() {
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            if (!shutdown) {
                shutdown = true
                events += "executor"
            }
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown()
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }

    companion object {
        private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
            try {
                block()
            } catch (error: Throwable) {
                if (error is T) return error
                throw AssertionError("expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
            }
            throw AssertionError("expected ${T::class.java.simpleName}")
        }
    }
}
