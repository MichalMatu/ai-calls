package pl.michalmatu.aicallbridge.session

import java.io.EOFException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimePcmFrameAdapter
import pl.michalmatu.aicallbridge.realtime.RealtimeTransport

data class CallRealtimeAudioPumpSnapshot(
    val running: Boolean,
    val queuedOutputBytes: Long,
    val terminalReason: String?,
)

/**
 * Non-owning bidirectional worker between one active telephony PCM bridge and one connected
 * Realtime transport.
 *
 * The coordinator remains the sole owner of the endpoint lease. The session orchestrator remains
 * the owner of the Realtime transport. This pump only moves PCM between them and reports the first
 * terminal data-plane failure so the orchestrator can trigger whole-generation cleanup.
 */
class CallRealtimeAudioPump(
    private val bridge: CallRealtimePcmBridge,
    private val transport: RealtimeTransport,
    private val monotonicNs: () -> Long = System::nanoTime,
    private val onTerminalFailure: (Throwable) -> Unit,
    private val functionCallHandler: (RealtimeFunctionCall) -> Unit = {},
) : RealtimeTransport.Listener, AutoCloseable {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val terminalError = AtomicReference<Throwable?>(null)
    private val rxThread = AtomicReference<Thread?>(null)
    private val txThread = AtomicReference<Thread?>(null)

    private val outputLock = ReentrantLock()
    private val outputAvailable = outputLock.newCondition()
    private val outputQueue = ArrayDeque<PcmFrame>()
    private var queuedOutputBytes = 0L

    @Synchronized
    fun start() {
        check(!closed.get()) { "Realtime audio pump is already closed" }
        check(started.compareAndSet(false, true)) { "Realtime audio pump can only be started once" }

        running.set(true)
        transport.setListener(this)

        val tx = Thread(::runUplink, "call-realtime-tx").apply { isDaemon = true }
        val rx = Thread(::runDownlink, "call-realtime-rx").apply { isDaemon = true }
        txThread.set(tx)
        rxThread.set(rx)

        try {
            tx.start()
            rx.start()
        } catch (error: Throwable) {
            signalTerminal(error)
            throw error
        }
    }

    fun snapshot(): CallRealtimeAudioPumpSnapshot {
        val queuedBytes = outputLock.withLock { queuedOutputBytes }
        return CallRealtimeAudioPumpSnapshot(
            running = running.get(),
            queuedOutputBytes = queuedBytes,
            terminalReason = terminalError.get()?.let(::describe),
        )
    }

    override fun onAudio(frame: PcmFrame) {
        if (!running.get()) return

        try {
            enqueueRealtimeOutput(frame)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    /**
     * Counterparty speech is a local barge-in signal: immediately discard buffered assistant audio
     * and cancel the current model response. A socket-send failure is terminal; a later server-side
     * error event is not automatically terminal because the Realtime API may report recoverable
     * request errors through the generic error event.
     */
    override fun onRemoteSpeechStarted() {
        if (!running.get()) return
        clearOutputQueue()
        transport.cancelResponse().exceptionOrNull()?.let(::signalTerminal)
    }

    override fun onRemoteSpeechStopped() = Unit

    override fun onFunctionCall(call: RealtimeFunctionCall) {
        if (!running.get()) return
        try {
            functionCallHandler(call)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    override fun onError(error: Throwable) {
        // Do not fail the call on every generic Realtime error event. A lost socket becomes a
        // terminal sendAudio failure on the next 20 ms downlink frame; helper/PFD failures are
        // independently covered by the call-media coordinator fail-safe paths.
    }

    /**
     * Stops local workers only. Endpoint and transport ownership deliberately remain outside this
     * class so TAKE OVER can keep its existing coordinator-first safety ordering.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        transport.setListener(null)
        clearOutputQueue()
        rxThread.get()?.interrupt()
        txThread.get()?.interrupt()
    }

    private fun runDownlink() {
        while (running.get()) {
            try {
                val frame = bridge.readRealtimeInputFrame(monotonicNs())
                if (frame == null) {
                    if (running.get()) {
                        signalTerminal(EOFException("telephony downlink closed"))
                    }
                    return
                }
                if (!running.get()) return

                val result = transport.sendAudio(frame)
                val error = result.exceptionOrNull()
                if (error != null) {
                    signalTerminal(error)
                    return
                }
            } catch (error: Throwable) {
                if (running.get()) {
                    signalTerminal(error)
                }
                return
            }
        }
    }

    private fun runUplink() {
        while (running.get()) {
            val frame = try {
                takeOutputChunk()
            } catch (error: InterruptedException) {
                if (!running.get()) return
                continue
            }

            if (frame == null) {
                if (!running.get()) return
                continue
            }

            try {
                bridge.writeRealtimeOutputFrame(frame)
            } catch (error: Throwable) {
                if (running.get()) {
                    signalTerminal(error)
                }
                return
            }
        }
    }

    private fun enqueueRealtimeOutput(frame: PcmFrame) {
        val format = frame.format
        require(format.sampleRateHz == RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ) {
            "Realtime output must be 24000 Hz"
        }
        require(format.channels == 1 && format.bitsPerSample == 16) {
            "Realtime output must be mono PCM16LE"
        }
        val data = frame.data
        require((data.size and 1) == 0) { "Realtime output must contain whole PCM16 samples" }

        var offset = 0
        while (offset < data.size && running.get()) {
            val length = minOf(MAX_OUTPUT_CHUNK_BYTES, data.size - offset)
            val sampleOffset = offset / 2L
            val timestampNs = frame.monotonicTimestampNs +
                sampleOffset * NANOS_PER_SECOND / RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ
            val chunk = PcmFrame(
                format,
                data.copyOfRange(offset, offset + length),
                timestampNs,
            )
            if (!enqueueOutput(chunk)) return
            offset += length
        }
    }

    private fun enqueueOutput(frame: PcmFrame): Boolean {
        var overflow = false
        var accepted = false
        outputLock.withLock {
            if (!running.get()) return false
            val nextBytes = queuedOutputBytes + frame.data.size
            if (
                nextBytes > MAX_OUTPUT_BACKLOG_BYTES ||
                    outputQueue.size >= MAX_OUTPUT_QUEUE_CHUNKS
            ) {
                overflow = true
            } else {
                outputQueue.addLast(frame)
                queuedOutputBytes = nextBytes
                accepted = true
                outputAvailable.signal()
            }
        }

        if (overflow) {
            signalTerminal(
                IllegalStateException(
                    "Realtime output backlog exceeded bounded queue limits " +
                        "(${MAX_OUTPUT_BACKLOG_MS} ms / $MAX_OUTPUT_QUEUE_CHUNKS chunks)",
                ),
            )
        }
        return accepted
    }

    @Throws(InterruptedException::class)
    private fun takeOutputChunk(): PcmFrame? {
        outputLock.lockInterruptibly()
        try {
            while (outputQueue.isEmpty() && running.get()) {
                outputAvailable.await()
            }
            if (!running.get() || outputQueue.isEmpty()) return null

            val frame = outputQueue.removeFirst()
            queuedOutputBytes -= frame.data.size
            return frame
        } finally {
            outputLock.unlock()
        }
    }

    private fun clearOutputQueue() {
        outputLock.withLock {
            outputQueue.clear()
            queuedOutputBytes = 0L
            outputAvailable.signalAll()
        }
    }

    private fun signalTerminal(error: Throwable) {
        if (closed.get()) return
        if (!terminalError.compareAndSet(null, error)) return

        running.set(false)
        transport.setListener(null)
        clearOutputQueue()
        rxThread.get()?.interrupt()
        txThread.get()?.interrupt()

        try {
            onTerminalFailure(error)
        } catch (_: Throwable) {
            // The data plane is already stopped. The orchestrator owns external cleanup/telemetry.
        }
    }

    private fun describe(error: Throwable): String {
        val type = error.javaClass.simpleName
        val message = error.message?.replace('\n', ' ')?.replace('\r', ' ')
        return if (message.isNullOrBlank()) type else "$type:$message"
    }

    private companion object {
        const val MAX_OUTPUT_BACKLOG_MS = 500
        const val MAX_OUTPUT_QUEUE_CHUNKS = 64
        const val MAX_OUTPUT_CHUNK_BYTES =
            RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ * 2 * 20 / 1_000
        const val MAX_OUTPUT_BACKLOG_BYTES =
            RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ * 2 * MAX_OUTPUT_BACKLOG_MS / 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
