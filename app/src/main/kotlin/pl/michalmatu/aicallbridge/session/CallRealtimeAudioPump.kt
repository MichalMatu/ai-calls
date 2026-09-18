package pl.michalmatu.aicallbridge.session

import java.io.EOFException
import java.util.ArrayDeque
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import pl.michalmatu.aicallbridge.audio.PcmFrame
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeOutputPartId
import pl.michalmatu.aicallbridge.realtime.RealtimePcmFrameAdapter
import pl.michalmatu.aicallbridge.realtime.RealtimeResponseStatus
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
 *
 * When [outputApprovalPolicy] is supplied, identified model audio is buffered as a complete
 * Realtime output part and cannot reach telephony TX until audio.done, transcript.done and a
 * successful response.done have arrived and the app-owned policy explicitly releases the final
 * transcript. This is defense in depth; the Realtime transcript is not treated as a cryptographic
 * proof of audio contents.
 */
class CallRealtimeAudioPump(
    private val bridge: CallRealtimePcmBridge,
    private val transport: RealtimeTransport,
    private val monotonicNs: () -> Long = System::nanoTime,
    private val functionCallHandler: (RealtimeFunctionCall) -> Unit = {},
    outputApprovalPolicy: CallRealtimeOutputApprovalPolicy? = null,
    private val onTerminalFailure: (Throwable) -> Unit,
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
    private val gatedReleasedQueue = ArrayDeque<PcmFrame>()
    private var queuedOutputBytes = 0L
    private var gatedReleasedBytes = 0L

    private val responseBuffer = outputApprovalPolicy?.let(::CallRealtimeOutputResponseBuffer)
    private val gateLock = Any()
    private val activeGatedParts = LinkedHashSet<RealtimeOutputPartId>()
    private val suppressedResponseIds = LinkedHashSet<String>()

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
        val pendingBytes = responseBuffer?.snapshot()?.bufferedAudioBytes ?: 0L
        val queuedBytes = outputLock.withLock { queuedOutputBytes + gatedReleasedBytes }
        return CallRealtimeAudioPumpSnapshot(
            running = running.get(),
            queuedOutputBytes = queuedBytes + pendingBytes,
            terminalReason = terminalError.get()?.let(::describe),
        )
    }

    /** Legacy/unidentified output remains valid only when the full-response speech gate is off. */
    override fun onAudio(frame: PcmFrame) {
        if (!running.get()) return
        if (responseBuffer != null) {
            signalTerminal(
                IllegalStateException("Realtime output is missing identity while speech gate is enabled"),
            )
            return
        }

        try {
            enqueueRealtimeOutput(frame)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    override fun onOutputAudio(partId: RealtimeOutputPartId, frame: PcmFrame) {
        if (!running.get()) return
        val gate = responseBuffer
        if (gate == null) {
            onAudio(frame)
            return
        }

        try {
            forEachRealtimeOutputChunk(frame) { chunk ->
                gatedEvent(partId) { it.onAudio(partId, chunk) }?.let(::handleGatedResult)
            }
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    override fun onOutputAudioTranscriptDelta(partId: RealtimeOutputPartId, delta: String) {
        if (!running.get()) return
        val gate = responseBuffer ?: return
        try {
            gatedEvent(partId) { gate.onTranscriptDelta(partId, delta) }?.let(::handleGatedResult)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    override fun onOutputAudioTranscriptDone(partId: RealtimeOutputPartId, transcript: String) {
        if (!running.get()) return
        val gate = responseBuffer ?: return
        try {
            gatedEvent(partId) { gate.onTranscriptDone(partId, transcript) }?.let(::handleGatedResult)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    override fun onOutputAudioDone(partId: RealtimeOutputPartId) {
        if (!running.get()) return
        val gate = responseBuffer ?: return
        try {
            gatedEvent(partId) { gate.onAudioDone(partId) }?.let(::handleGatedResult)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    override fun onResponseDone(responseId: String, status: RealtimeResponseStatus) {
        if (!running.get()) return
        val gate = responseBuffer ?: return
        try {
            val results = synchronized(gateLock) {
                if (responseId in suppressedResponseIds) {
                    return@synchronized emptyList<CallRealtimeOutputBufferResult>()
                }
                val finalized = gate.onResponseDone(responseId, status)
                activeGatedParts.removeAll { partId -> partId.responseId == responseId }
                finalized
            }
            results.forEach(::handleGatedResult)
        } catch (error: Throwable) {
            signalTerminal(error)
        }
    }

    /**
     * Counterparty speech is a local barge-in signal: immediately discard buffered assistant audio
     * and cancel the current model response. Known response ids are tombstoned so late server events
     * from the cancelled response cannot rebuild and release an already-discarded output part.
     */
    override fun onRemoteSpeechStarted() {
        if (!running.get()) return
        discardGatedResponseForBargeIn()
        clearOutputQueues()
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
        resetAllOutputState()
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
        forEachRealtimeOutputChunk(frame) { chunk ->
            enqueueOutput(chunk)
        }
    }

    private fun forEachRealtimeOutputChunk(frame: PcmFrame, action: (PcmFrame) -> Unit) {
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
            action(
                PcmFrame(
                    format,
                    data.copyOfRange(offset, offset + length),
                    timestampNs,
                ),
            )
            offset += length
        }
    }

    private fun gatedEvent(
        partId: RealtimeOutputPartId,
        action: (CallRealtimeOutputResponseBuffer) -> CallRealtimeOutputBufferResult,
    ): CallRealtimeOutputBufferResult? {
        val gate = responseBuffer ?: return null
        return synchronized(gateLock) {
            if (partId.responseId in suppressedResponseIds) {
                return@synchronized null
            }
            activeGatedParts += partId
            val result = action(gate)
            if (
                result is CallRealtimeOutputBufferResult.Released ||
                    result is CallRealtimeOutputBufferResult.Dropped
            ) {
                activeGatedParts.remove(partId)
            }
            result
        }
    }

    private fun handleGatedResult(result: CallRealtimeOutputBufferResult) {
        when (result) {
            CallRealtimeOutputBufferResult.Pending -> Unit
            is CallRealtimeOutputBufferResult.Dropped -> Unit
            is CallRealtimeOutputBufferResult.Released -> enqueueGatedRelease(result.output.frames)
        }
    }

    private fun enqueueGatedRelease(frames: List<PcmFrame>) {
        val bytes = frames.sumOf { it.data.size.toLong() }
        var overflow = false
        outputLock.withLock {
            if (!running.get()) return
            if (gatedReleasedBytes + bytes > MAX_GATED_RELEASE_BACKLOG_BYTES) {
                overflow = true
            } else {
                for (frame in frames) {
                    gatedReleasedQueue.addLast(frame)
                }
                gatedReleasedBytes += bytes
                outputAvailable.signal()
            }
        }
        if (overflow) {
            signalTerminal(
                IllegalStateException("Realtime gated release exceeded bounded output backlog"),
            )
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
            while (outputQueue.isEmpty() && gatedReleasedQueue.isEmpty() && running.get()) {
                outputAvailable.await()
            }
            if (!running.get()) return null

            if (outputQueue.isNotEmpty()) {
                val frame = outputQueue.removeFirst()
                queuedOutputBytes -= frame.data.size
                return frame
            }
            if (gatedReleasedQueue.isNotEmpty()) {
                val frame = gatedReleasedQueue.removeFirst()
                gatedReleasedBytes -= frame.data.size
                return frame
            }
            return null
        } finally {
            outputLock.unlock()
        }
    }

    private fun discardGatedResponseForBargeIn() {
        val gate = responseBuffer ?: return
        synchronized(gateLock) {
            for (partId in activeGatedParts) {
                suppressedResponseIds += partId.responseId
            }
            trimSuppressedResponses()
            activeGatedParts.clear()
            gate.clear()
        }
    }

    private fun trimSuppressedResponses() {
        while (suppressedResponseIds.size > MAX_SUPPRESSED_RESPONSE_IDS) {
            val iterator = suppressedResponseIds.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    private fun clearOutputQueues() {
        outputLock.withLock {
            outputQueue.clear()
            gatedReleasedQueue.clear()
            queuedOutputBytes = 0L
            gatedReleasedBytes = 0L
            outputAvailable.signalAll()
        }
    }

    private fun resetAllOutputState() {
        clearOutputQueues()
        responseBuffer?.let { gate ->
            synchronized(gateLock) {
                gate.clear()
                activeGatedParts.clear()
                suppressedResponseIds.clear()
            }
        }
    }

    private fun signalTerminal(error: Throwable) {
        if (closed.get()) return
        if (!terminalError.compareAndSet(null, error)) return

        running.set(false)
        transport.setListener(null)
        resetAllOutputState()
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
        const val MAX_GATED_RESPONSE_SECONDS = 12
        const val MAX_GATED_RELEASE_BACKLOG_BYTES =
            RealtimePcmFrameAdapter.REALTIME_SAMPLE_RATE_HZ * 2L * MAX_GATED_RESPONSE_SECONDS
        const val MAX_SUPPRESSED_RESPONSE_IDS = 64
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
