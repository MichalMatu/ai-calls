package pl.michalmatu.aicallbridge.session

import java.io.EOFException
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.PcmEndOfUtteranceDetector

/**
 * Adapter onto the existing Android STT -> text model -> approval -> local TTS
 * pipeline. Never bypasses product-owned speech approval. The caller supplies
 * a ready, owned backend and its workflow approval policy to the pipeline.
 */
internal class S8LocalSpeechTurnAdapter(
    private val pipeline: LocalSpeechTextPipeline,
) : S8VoipLocalSpeechSession.SpeechTurn {
    override fun start(listener: S8VoipLocalSpeechSession.SpeechTurn.Listener) =
        pipeline.start(object : LocalSpeechTextPipeline.Listener {
            override fun onSpeechInputReady() = listener.onReady()
            override fun onUserTranscript(text: String) = listener.onTranscript()
            override fun onApprovedText(text: String) = listener.onApproved()
            override fun onOutputPcm16Mono16k(pcm: ByteArray) = listener.onOutputPcm16(pcm)
            override fun onDroppedText() = listener.onDropped()
            override fun onError(reason: String) = listener.onError()
        })

    override fun writeInputPcm(frame: ByteArray): Boolean = pipeline.writeInputPcm(frame)
    override fun finishInput() = pipeline.finishInput()
    override fun cancel() = pipeline.cancel()
    override fun close() = pipeline.close()
}

/**
 * Bounded half-duplex VoIP media + on-device speech owner.
 *
 * The ACTIVE [CallMediaSessionCoordinator] lease MUST be app-owned 16k PCM
 * (e.g. S8 8k RTP/PCMU via RtpPcm16kAdapterMediaPort). Each turn:
 * RX PCM -> local STT -> local text backend + approval -> local TTS -> TX PCM.
 * Reuses LocalSpeechTextPipeline; no model fallback or server inference.
 *
 * It does NOT create a SIP dialog, authenticate a remote peer, send a call,
 * record audio or provide GSM PCM access. Callers MUST first establish an
 * authorized, encrypted VoIP session. This owner closes coordinator media
 * BEFORE speech cancellation on every STOP/failure.
 *
 * App lifecycle calls start() from main thread; next-turn starts are posted
 * through [speechExecutor] (on Android use context.mainExecutor). Raw audio
 * and transcripts are not retained in snapshots, logs or Git.
 */
internal class S8VoipLocalSpeechSession(
    private val coordinator: CallMediaSessionCoordinator,
    private val generation: Long,
    private val speech: SpeechTurn,
    private val speechExecutor: Executor,
    private val maxTurns: Int = 6,
    private val observer: (Snapshot) -> Unit = {},
) : AutoCloseable {
    internal interface SpeechTurn : AutoCloseable {
        interface Listener {
            fun onReady()
            fun onTranscript()
            fun onApproved()
            fun onOutputPcm16(pcm: ByteArray)
            fun onDropped()
            fun onError()
        }
        fun start(listener: Listener)
        fun writeInputPcm(frame: ByteArray): Boolean
        fun finishInput()
        fun cancel()
    }

    enum class Phase { READY, LISTENING, PROCESSING, SPEAKING, COMPLETE, STOPPED, FAILED }

    data class Snapshot(
        val phase: Phase, val turn: Int, val incomingFrames: Int,
        val outgoingFrames: Int, val transcripts: Int, val approvals: Int,
    )

    private val lock = Any()
    private val closed = AtomicBoolean()
    private val lease: CallMediaEndpointLease = coordinator.activeEndpointLease(generation)
    private var phase = Phase.READY
    private var turn = 0
    private var incomingFrames = 0
    private var outgoingFrames = 0
    private var transcripts = 0
    private var approvals = 0
    private var approvedTurn = 0
    private var receiver: Thread? = null
    private var sender: Thread? = null

    init {
        require(maxTurns in 1..6) { "s8_voip_turn_count_out_of_bounds" }
    }

    fun snapshot(): Snapshot = synchronized(lock) { snapshotLocked() }

    fun start() {
        synchronized(lock) {
            check(phase == Phase.READY && !closed.get()) { "s8_voip_session_already_started" }
            phase = Phase.LISTENING
            turn = 1
        }
        publish()
        launchSpeechTurn(1)
    }

    private fun launchSpeechTurn(expectedTurn: Int) {
        try {
            speechExecutor.execute {
                if (!current(expectedTurn, Phase.LISTENING)) return@execute
                try {
                    speech.start(listenerFor(expectedTurn))
                } catch (_: Throwable) {
                    fail()
                }
            }
        } catch (_: RuntimeException) {
            fail()
        }
    }

    private fun listenerFor(expectedTurn: Int) = object : SpeechTurn.Listener {
        override fun onReady() {
            if (!current(expectedTurn, Phase.LISTENING)) return
            val thread = Thread({ capture(expectedTurn) }, "s8-voip-local-stt-rx").apply {
                isDaemon = true
            }
            synchronized(lock) {
                if (closed.get() || turn != expectedTurn || phase != Phase.LISTENING) return
                receiver = thread
            }
            thread.start()
        }

        override fun onTranscript() {
            synchronized(lock) {
                if (!closed.get() && turn == expectedTurn && phase == Phase.PROCESSING) {
                    transcripts++
                }
            }
            publish()
        }

        override fun onApproved() {
            synchronized(lock) {
                if (!closed.get() && turn == expectedTurn && phase == Phase.PROCESSING) {
                    if (approvedTurn != expectedTurn) {
                        approvedTurn = expectedTurn
                        approvals++
                    }
                }
            }
            publish()
        }

        override fun onOutputPcm16(pcm: ByteArray) {
            if (!current(expectedTurn, Phase.PROCESSING)) return
            // A callback alone is not authorization. The exact turn must
            // previously pass the product-owned complete-text approval gate.
            if (synchronized(lock) { approvedTurn != expectedTurn }) {
                fail()
                return
            }
            if (pcm.isEmpty() || pcm.size % 2 != 0 || pcm.size > MAX_TTS_BYTES) {
                fail()
                return
            }
            synchronized(lock) {
                if (closed.get() || turn != expectedTurn || phase != Phase.PROCESSING) return
                phase = Phase.SPEAKING
            }
            publish()
            val ownedPcm = pcm.copyOf()
            val thread = Thread(
                { transmit(expectedTurn, ownedPcm) }, "s8-voip-local-tts-tx"
            ).apply { isDaemon = true }
            synchronized(lock) {
                if (closed.get() || turn != expectedTurn) return
                sender = thread
            }
            thread.start()
        }

        override fun onDropped() {
            if (current(expectedTurn, Phase.PROCESSING)) fail()
        }
        override fun onError() {
            if (current(expectedTurn, Phase.LISTENING) ||
                current(expectedTurn, Phase.PROCESSING) ||
                current(expectedTurn, Phase.SPEAKING)) fail()
        }
    }

    private fun capture(expectedTurn: Int) {
        val detector = PcmEndOfUtteranceDetector(maxCaptureMs = 8_000)
        val pcm = ByteArray(FRAME_16K_BYTES)
        try {
            while (current(expectedTurn, Phase.LISTENING)) {
                var count = 0
                while (count < pcm.size && current(expectedTurn, Phase.LISTENING)) {
                    val read = lease.downlink().read(pcm, count, pcm.size - count)
                    if (read < 0) throw EOFException("s8_voip_rx_stopped")
                    if (read == 0) continue
                    count += read
                }
                if (!current(expectedTurn, Phase.LISTENING)) return
                if (!speech.writeInputPcm(pcm)) throw IOException("s8_voip_stt_rejected_pcm")
                synchronized(lock) { incomingFrames++ }
                val endpoint = detector.acceptPcm16(pcm)
                if (endpoint.shouldStop) {
                    synchronized(lock) {
                        if (closed.get() || turn != expectedTurn || phase != Phase.LISTENING) return
                        phase = Phase.PROCESSING
                    }
                    publish()
                    speech.finishInput()
                    return
                }
            }
        } catch (_: Throwable) {
            if (!closed.get()) fail()
        }
    }

    private fun transmit(expectedTurn: Int, bytes: ByteArray) {
        try {
            var offset = 0
            while (offset < bytes.size && current(expectedTurn, Phase.SPEAKING)) {
                val frame = ByteArray(FRAME_16K_BYTES)
                val available = minOf(frame.size, bytes.size - offset)
                System.arraycopy(bytes, offset, frame, 0, available)
                lease.uplink().write(frame)
                synchronized(lock) { outgoingFrames++ }
                offset += available
            }
            if (!current(expectedTurn, Phase.SPEAKING)) return
            if (expectedTurn == maxTurns) {
                finish(Phase.COMPLETE)
            } else {
                synchronized(lock) {
                    if (closed.get() || turn != expectedTurn || phase != Phase.SPEAKING) return
                    phase = Phase.LISTENING
                    turn++
                }
                publish()
                launchSpeechTurn(expectedTurn + 1)
            }
        } catch (_: Throwable) {
            if (!closed.get()) fail()
        } finally {
            java.util.Arrays.fill(bytes, 0.toByte())
        }
    }

    private fun current(expectedTurn: Int, required: Phase): Boolean =
        synchronized(lock) { !closed.get() && turn == expectedTurn && phase == required }

    private fun snapshotLocked() = Snapshot(
        phase, turn, incomingFrames, outgoingFrames, transcripts, approvals,
    )

    private fun publish() {
        val value = snapshot()
        try { observer(value) } catch (_: Throwable) { /* Observers own no media. */ }
    }

    private fun fail() { finish(Phase.FAILED) }

    override fun close() { finish(Phase.STOPPED) }

    private fun finish(terminal: Phase) {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { phase = terminal }
        // Local STOP first; never wait for STT, model, TTS or TLS to finish.
        coordinator.takeOverNow()
        receiver?.interrupt()
        sender?.interrupt()
        // The Android STT/TTS adapters own main-thread-only platform APIs.
        // Run their cleanup on the same executor that started the recognizer,
        // never directly on the RTP receiver/transmitter worker.
        try {
            speechExecutor.execute {
                try { speech.cancel() } catch (_: Throwable) {}
                try { speech.close() } catch (_: Throwable) {}
            }
        } catch (_: RuntimeException) { /* Local media is already stopped. */ }
        publish()
    }

    private companion object {
        const val FRAME_16K_BYTES = 16_000 * 2 * 20 / 1_000
        const val MAX_TTS_BYTES = 16_000 * 2 * 30
    }
}
