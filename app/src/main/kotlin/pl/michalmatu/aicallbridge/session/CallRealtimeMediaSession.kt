package pl.michalmatu.aicallbridge.session

import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

enum class CallRealtimeMediaSessionState {
    ATTACHED,
    ACTIVE,
    STOPPING,
    TAKEN_OVER,
    FAILED,
}

data class CallRealtimeMediaSessionSnapshot(
    val generation: Long,
    val state: CallRealtimeMediaSessionState,
    val failureReason: String?,
)

/**
 * Safety owner for one already-connected Realtime transport attached to one ACTIVE call-media
 * generation.
 *
 * Local telephony cleanup always happens first through CallMediaSessionCoordinator. Pump shutdown
 * and Realtime cancel/close happen only afterwards, so human TAKE OVER never depends on model or
 * network completion.
 */
class CallRealtimeMediaSession(
    private val coordinator: CallMediaSessionCoordinator,
    private val generation: Long,
    private val transport: RealtimeTransport,
    monotonicNs: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val lock = Any()
    private var state = CallRealtimeMediaSessionState.ATTACHED
    private var failureReason: String? = null

    private val pump: CallRealtimeAudioPump

    init {
        val lease = coordinator.activeEndpointLease(generation)
        pump = CallRealtimeAudioPump(
            CallRealtimePcmBridge(lease),
            transport,
            monotonicNs,
            ::handlePumpFailure,
        )
    }

    fun start() {
        synchronized(lock) {
            check(state == CallRealtimeMediaSessionState.ATTACHED) {
                "cannot start Realtime media session from $state"
            }
            state = CallRealtimeMediaSessionState.ACTIVE
        }

        try {
            pump.start()
        } catch (error: Throwable) {
            handlePumpFailure(error)
            throw error
        }
    }

    fun snapshot(): CallRealtimeMediaSessionSnapshot =
        synchronized(lock) {
            CallRealtimeMediaSessionSnapshot(
                generation = generation,
                state = state,
                failureReason = failureReason,
            )
        }

    /**
     * Immediate local human takeover. The coordinator closes the app-owned endpoints and dispatches
     * privileged abort/unbind before any Realtime cleanup is attempted.
     */
    fun takeOverNow() {
        val shouldCleanup = synchronized(lock) {
            when (state) {
                CallRealtimeMediaSessionState.TAKEN_OVER,
                CallRealtimeMediaSessionState.FAILED,
                CallRealtimeMediaSessionState.STOPPING,
                -> false
                CallRealtimeMediaSessionState.ATTACHED,
                CallRealtimeMediaSessionState.ACTIVE,
                -> {
                    state = CallRealtimeMediaSessionState.STOPPING
                    true
                }
            }
        }
        if (!shouldCleanup) return

        coordinator.takeOverNow()
        pump.close()
        cleanupRealtimeBestEffort()

        synchronized(lock) {
            if (state == CallRealtimeMediaSessionState.STOPPING) {
                failureReason = null
                state = CallRealtimeMediaSessionState.TAKEN_OVER
            }
        }
    }

    override fun close() {
        takeOverNow()
    }

    private fun handlePumpFailure(error: Throwable) {
        val shouldCleanup = synchronized(lock) {
            if (state != CallRealtimeMediaSessionState.ACTIVE) {
                false
            } else {
                failureReason = describe(error)
                state = CallRealtimeMediaSessionState.STOPPING
                true
            }
        }
        if (!shouldCleanup) return

        coordinator.takeOverNow()
        pump.close()
        cleanupRealtimeBestEffort()

        synchronized(lock) {
            if (state == CallRealtimeMediaSessionState.STOPPING) {
                state = CallRealtimeMediaSessionState.FAILED
            }
        }
    }

    private fun cleanupRealtimeBestEffort() {
        try {
            transport.cancelResponse()
        } catch (_: Throwable) {
            // Local endpoint close + privileged abort already happened; network cleanup is best effort.
        }
        try {
            transport.close()
        } catch (_: Throwable) {
            // Realtime cleanup must never weaken local TAKE OVER.
        }
    }

    private fun describe(error: Throwable): String {
        val type = error.javaClass.simpleName
        val message = error.message?.replace('\n', ' ')?.replace('\r', ' ')
        return if (message.isNullOrBlank()) type else "$type:$message"
    }
}
