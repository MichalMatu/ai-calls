package pl.michalmatu.aicallbridge.developerrelay

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.PcmEndOfUtteranceDetector
import pl.michalmatu.aicallbridge.session.CallMediaEndpointLease
import pl.michalmatu.aicallbridge.session.CallMediaSessionRuntime
import pl.michalmatu.aicallbridge.session.CallMediaSessionSnapshot
import pl.michalmatu.aicallbridge.session.CallMediaSessionState
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy

/**
 * Developer-only bounded multi-turn proof:
 * frozen telephony RX -> local STT -> interactive chat relay -> approval -> local TTS -> frozen TX.
 *
 * It intentionally reuses production speech/backend/approval boundaries and owns no model runtime.
 */
internal object ChatRelayLiveCallProbe {
    const val REPORT_FILE = "chat-relay-live-call-report.txt"
    const val MAX_TURNS = 5
    private const val RESPONSE_TIMEOUT_MS = 90_000L

    fun run(
        context: Context,
        sessionId: String,
        maxTurns: Int,
        callback: (String) -> Unit,
    ) {
        ChatRelayEnvelope(sessionId, 1, "probe").validate()
        require(maxTurns in 1..MAX_TURNS) { "invalid_relay_max_turns" }
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null || audioManager.mode != AudioManager.MODE_IN_CALL) {
            callback(immediateReport("cellular_call_not_active"))
            return
        }
        Run(appContext, sessionId, maxTurns, callback).start()
    }

    private class Run(
        private val context: Context,
        private val sessionId: String,
        private val maxTurns: Int,
        private val callback: (String) -> Unit,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)
        private val mediaTurnStarted = AtomicBoolean(false)
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ChatRelayLiveCall").apply { isDaemon = true }
        }
        private val mailbox = ChatRelayMailbox(File(context.filesDir, ChatRelayMailbox.DIRECTORY_NAME))
        private val backend = InteractiveChatRelayBackend(
            mailbox = mailbox,
            sessionId = sessionId,
            responseTimeoutMs = RESPONSE_TIMEOUT_MS,
        )
        private val workflow = activeWorkflow()
        private val pipeline = LocalSpeechTextPipeline(
            context,
            backend,
            CallTextAgentOutputApprovalPolicy(
                workflow,
                CallCommitmentGate { "chat-relay-live-token" },
            ),
        )
        private val lines = mutableListOf(
            "probe=chat_relay_live_call",
            "developer_relay=true",
            "call_required=true",
            "session_id=$sessionId",
            "max_turns=$maxTurns",
            "approval_policy=application_owned",
            "endpointing=trailing_silence",
            "timestamp_utc=${Instant.now()}",
        )
        private var mediaRuntime: CallMediaSessionRuntime? = null
        private var downlinkPump: ChatRelayDownlinkPump? = null
        private var turnsCompleted = 0
        private var currentTurn = 0
        private var turnStartedAtMs = 0L
        private var estimatedSpeechEndElapsedMs: Long? = null
        private var totalRxBytes = 0L
        private var totalTxBytes = 0L

        private val timeout = Runnable { finish(false, "probe_timeout") }

        fun start() {
            mailbox.clear()
            try {
                mediaRuntime = CallMediaSessionRuntime(
                    context,
                    Consumer { snapshot -> onMediaSnapshot(snapshot) },
                )
                handler.postDelayed(timeout, maxTurns * 105_000L + 10_000L)
                val generation = mediaRuntime!!.coordinator().start(LocalSpeechFormat.SAMPLE_RATE_HZ)
                lines += "media_generation=$generation"
            } catch (error: Throwable) {
                finish(false, "media_launch_${error.javaClass.simpleName}")
            }
        }

        private fun onMediaSnapshot(snapshot: CallMediaSessionSnapshot) {
            if (finished.get()) return
            when (snapshot.state) {
                CallMediaSessionState.ACTIVE -> {
                    if (mediaTurnStarted.compareAndSet(false, true)) {
                        context.mainExecutor.execute { startTurns(snapshot.generation) }
                    }
                }
                CallMediaSessionState.FAILED -> context.mainExecutor.execute {
                    finish(false, "media_${snapshot.failure.name.lowercase()}")
                }
                else -> Unit
            }
        }

        private fun startTurns(generation: Long) {
            if (finished.get()) return
            val lease = try {
                mediaRuntime?.coordinator()?.activeEndpointLease(generation)
                    ?: throw IllegalStateException("media_runtime_unavailable")
            } catch (error: Throwable) {
                finish(false, "endpoint_${error.javaClass.simpleName}")
                return
            }
            lines += "endpoint_lease_acquired=true"
            try {
                downlinkPump = ChatRelayDownlinkPump(
                    input = lease.downlink(),
                    frameBytes = LocalSpeechFormat.bytesForDurationMs(20),
                ).also { it.start() }
                lines += "downlink_pump_started=true"
            } catch (error: Throwable) {
                finish(false, "downlink_pump_${error.javaClass.simpleName}")
                return
            }
            beginNextTurn(generation, lease)
        }

        private fun beginNextTurn(generation: Long, lease: CallMediaEndpointLease) {
            if (finished.get()) return
            if (turnsCompleted >= maxTurns) {
                finish(true)
                return
            }
            currentTurn = turnsCompleted + 1
            turnStartedAtMs = SystemClock.elapsedRealtime()
            estimatedSpeechEndElapsedMs = null
            lines += "turn_${currentTurn}_started=true"
            pipeline.start(object : LocalSpeechTextPipeline.Listener {
                override fun onSpeechInputReady() {
                    lines += "turn_${currentTurn}_stt_ready=true"
                    lines += "turn_${currentTurn}_stt_ready_elapsed_ms=${elapsedTurnMs()}"
                    beginCaptureInputTurn(currentTurn)
                }

                override fun onUserTranscript(text: String) {
                    lines += "turn_${currentTurn}_stt_chars=${text.length}"
                    lines += "turn_${currentTurn}_stt_elapsed_ms=${elapsedTurnMs()}"
                }

                override fun onApprovedText(text: String) {
                    lines += "turn_${currentTurn}_approved_chars=${text.length}"
                    lines += "turn_${currentTurn}_approved_elapsed_ms=${elapsedTurnMs()}"
                }

                override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                    lines += "turn_${currentTurn}_output_pcm_ready_elapsed_ms=${elapsedTurnMs()}"
                    executor.execute { writeOutputTurn(lease, pcm, currentTurn, generation) }
                }

                override fun onDroppedText() = finish(false, "output_dropped")
                override fun onError(reason: String) = finish(false, reason)
            })
        }

        private fun beginCaptureInputTurn(turn: Int) {
            val pump = downlinkPump ?: run {
                finish(false, "turn_${turn}_downlink_pump_unavailable")
                return
            }
            val detector = PcmEndOfUtteranceDetector()
            val captureStartedElapsedMs = elapsedTurnMs()
            var total = 0
            var endpoint = detector.acceptPcm16(ByteArray(0))
            try {
                pump.beginCapture(object : ChatRelayDownlinkPump.Capture {
                    override fun onFrame(bytes: ByteArray, length: Int): Boolean {
                        if (finished.get()) return false
                        if (!pipeline.writeInputPcm(bytes, 0, length)) {
                            throw IllegalStateException("stt_pcm_write_failed")
                        }
                        total += length
                        endpoint = detector.acceptPcm16(bytes, 0, length)
                        return !endpoint.shouldStop && !finished.get()
                    }

                    override fun onFinished() {
                        totalRxBytes += total.toLong()
                        lines += "turn_${turn}_rx_pcm_bytes=$total"
                        lines += "turn_${turn}_endpoint_reason=${endpoint.reason?.name?.lowercase() ?: "capture_finished"}"
                        lines += "turn_${turn}_endpoint_capture_ms=${endpoint.capturedMs}"
                        lines += "turn_${turn}_speech_detected=${endpoint.speechDetected}"
                        endpoint.estimatedSpeechEndMs?.let { speechEndMs ->
                            val estimated = captureStartedElapsedMs + speechEndMs
                            estimatedSpeechEndElapsedMs = estimated
                            lines += "turn_${turn}_estimated_eos_elapsed_ms=$estimated"
                        }
                        if (!endpoint.speechDetected) {
                            context.mainExecutor.execute { finish(false, "turn_${turn}_no_speech") }
                            return
                        }
                        pipeline.finishInput()
                    }

                    override fun onError(reason: String) {
                        if (finished.get()) return
                        context.mainExecutor.execute {
                            finish(false, "turn_${turn}_rx_${sanitize(reason)}")
                        }
                    }
                })
            } catch (error: Throwable) {
                finish(false, "turn_${turn}_rx_${error.javaClass.simpleName}")
            }
        }

        private fun writeOutputTurn(
            lease: CallMediaEndpointLease,
            pcm: ByteArray,
            turn: Int,
            generation: Long,
        ) {
            try {
                if (pcm.isEmpty()) throw IllegalStateException("tts_pcm_empty")
                val firstTxElapsed = elapsedTurnMs()
                val ttsPcmDurationMs = ChatRelayTurnPacing.pcmDurationMs(pcm.size)
                val postTxHoldMs = ChatRelayTurnPacing.postTxHoldMs(pcm.size)
                lines += "turn_${turn}_tts_pcm_duration_ms=$ttsPcmDurationMs"
                lines += "turn_${turn}_post_tx_hold_ms=$postTxHoldMs"
                lines += "turn_${turn}_first_tx_elapsed_ms=$firstTxElapsed"
                estimatedSpeechEndElapsedMs?.let { speechEndMs ->
                    lines += "turn_${turn}_eos_to_first_tx_ms=${(firstTxElapsed - speechEndMs).coerceAtLeast(0L)}"
                }
                val txWriteStarted = SystemClock.elapsedRealtime()
                lease.uplink().write(pcm)
                lease.uplink().flush()
                val txWriteWallMs =
                    (SystemClock.elapsedRealtime() - txWriteStarted).coerceAtLeast(0L)
                val postWriteHoldMs = ChatRelayTurnPacing.postWriteHoldMs(
                    pcmBytes = pcm.size,
                    txWriteWallMs = txWriteWallMs,
                )
                lines += "turn_${turn}_tx_write_wall_ms=$txWriteWallMs"
                lines += "turn_${turn}_post_write_hold_ms=$postWriteHoldMs"
                totalTxBytes += pcm.size.toLong()
                lines += "turn_${turn}_tx_pcm_bytes=${pcm.size}"
                handler.postDelayed(
                    { completeTurnAfterPlayback(turn, generation, lease) },
                    postWriteHoldMs,
                )
            } catch (error: Throwable) {
                context.mainExecutor.execute {
                    finish(false, "turn_${turn}_tx_${error.javaClass.simpleName}")
                }
            }
        }

        private fun completeTurnAfterPlayback(
            turn: Int,
            generation: Long,
            lease: CallMediaEndpointLease,
        ) {
            if (finished.get() || turn != currentTurn) return
            turnsCompleted = turn
            lines += "turn_${turn}_complete_elapsed_ms=${elapsedTurnMs()}"
            if (turnsCompleted >= maxTurns) {
                finish(true)
            } else {
                beginNextTurn(generation, lease)
            }
        }

        private fun elapsedTurnMs(): Long =
            (SystemClock.elapsedRealtime() - turnStartedAtMs).coerceAtLeast(0L)

        private fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacks(timeout)
            try { pipeline.close() } catch (_: Throwable) {}
            try { mailbox.clear() } catch (_: Throwable) {}
            downlinkPump?.let { pump ->
                lines += "downlink_discarded_pcm_bytes=${pump.discardedBytes()}"
                try { pump.close() } catch (_: Throwable) {}
            }
            downlinkPump = null
            try { mediaRuntime?.coordinator()?.takeOverNow() } catch (_: Throwable) {}
            try { mediaRuntime?.close() } catch (_: Throwable) {}
            mediaRuntime = null
            executor.shutdownNow()
            lines += "turns_completed=$turnsCompleted"
            lines += "telephony_rx_pcm_bytes=$totalRxBytes"
            lines += "telephony_tx_pcm_bytes=$totalTxBytes"
            lines += "chat_relay_live_call_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            try { File(context.filesDir, REPORT_FILE).writeText(report) } catch (_: Throwable) {}
            context.mainExecutor.execute { callback(report) }
        }
    }

    private fun activeWorkflow(): CallWorkflow {
        val task = CallTask(
            "Orange interactive ChatGPT relay test",
            "test STT plus TTS transport only; make no purchases, account changes, commitments or sensitive disclosures",
            "customer service",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(CallResolvedTarget("Orange support", "allowlisted"))
        workflow.markDialing()
        workflow.markCallActive()
        return workflow
    }

    private fun immediateReport(reason: String): String =
        "probe=chat_relay_live_call\n" +
            "chat_relay_live_call_success=false\n" +
            "failure_reason=$reason\n" +
            "probe_complete=true\n"

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(160)
}
