package pl.michalmatu.aicallbridge

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import pl.michalmatu.aicallbridge.textagent.LocalPhoneLlmBackendFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** One bounded live cellular turn: telephony RX -> local STT -> phone LLM -> approval -> TTS -> TX. */
internal object LocalPhoneLlmLiveCallProbe {
    private const val REPORT_FILE = "local-phone-llm-live-call-report.txt"
    private const val TIMEOUT_MS = 45_000L

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null || audioManager.mode != AudioManager.MODE_IN_CALL) {
            callback(immediateReport("cellular_call_not_active"))
            return
        }
        Run(appContext, callback).start()
    }

    private class Run(
        private val context: Context,
        private val callback: (String) -> Unit,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)
        private val mediaTurnStarted = AtomicBoolean(false)
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LocalPhoneLlmLiveTurn").apply { isDaemon = true }
        }
        private val lines = mutableListOf(
            "probe=local_phone_llm_live_call",
            "call_required=true",
            "backend_location=phone_loopback",
            "approval_policy=application_owned",
            "endpointing=trailing_silence",
            "timestamp_utc=${Instant.now()}",
            "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}",
        )
        private val backend = LocalPhoneLlmBackendFactory.create(context)
        private val workflow = activeWorkflow()
        private val pipeline = LocalSpeechTextPipeline(
            context,
            backend,
            CallTextAgentOutputApprovalPolicy(
                workflow,
                CallCommitmentGate { "live-local-phone-llm-token" },
            ),
        )
        private var mediaRuntime: CallMediaSessionRuntime? = null
        private var activeLease: CallMediaEndpointLease? = null
        private var turnStartedAtMs: Long = 0L
        private var estimatedSpeechEndElapsedMs: Long? = null

        private val timeout = Runnable { finish(false, "probe_timeout") }

        fun start() {
            try {
                mediaRuntime = CallMediaSessionRuntime(
                    context,
                    Consumer { snapshot -> onMediaSnapshot(snapshot) },
                )
                handler.postDelayed(timeout, TIMEOUT_MS)
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
                        context.mainExecutor.execute { beginTurn(snapshot.generation) }
                    }
                }
                CallMediaSessionState.FAILED -> {
                    context.mainExecutor.execute {
                        finish(false, "media_${snapshot.failure.name.lowercase()}")
                    }
                }
                else -> Unit
            }
        }

        private fun beginTurn(generation: Long) {
            if (finished.get()) return
            val lease = try {
                mediaRuntime?.coordinator()?.activeEndpointLease(generation)
                    ?: throw IllegalStateException("media_runtime_unavailable")
            } catch (error: Throwable) {
                finish(false, "endpoint_${error.javaClass.simpleName}")
                return
            }
            activeLease = lease
            lines += "media_active=true"
            turnStartedAtMs = SystemClock.elapsedRealtime()
            pipeline.start(object : LocalSpeechTextPipeline.Listener {
                override fun onSpeechInputReady() {
                    lines += "stt_ready=true"
                    executor.execute { captureInputTurn(lease) }
                }

                override fun onUserTranscript(text: String) {
                    lines += "stt_text=${sanitize(text)}"
                    lines += "stt_transcript_nonblank=${text.isNotBlank()}"
                    lines += "stt_elapsed_ms=${elapsedTurnMs()}"
                }

                override fun onApprovedText(text: String) {
                    lines += "backend_complete_response=true"
                    lines += "approved_text=${sanitize(text)}"
                    lines += "approved_text_nonblank=${text.isNotBlank()}"
                    lines += "llm_approved_elapsed_ms=${elapsedTurnMs()}"
                }

                override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                    lines += "output_tts_pcm_bytes=${pcm.size}"
                    executor.execute { writeOutputTurn(lease, pcm) }
                }

                override fun onDroppedText() = finish(false, "output_dropped")
                override fun onError(reason: String) = finish(false, reason)
            })
        }

        private fun captureInputTurn(lease: CallMediaEndpointLease) {
            val detector = PcmEndOfUtteranceDetector()
            val buffer = ByteArray(LocalSpeechFormat.bytesForDurationMs(20))
            val captureStartedElapsedMs = elapsedTurnMs()
            var total = 0
            var streamEnded = false
            var endpoint = detector.acceptPcm16(ByteArray(0))
            try {
                while (!endpoint.shouldStop && !finished.get()) {
                    val read = lease.downlink().read(buffer, 0, buffer.size)
                    if (read < 0) {
                        streamEnded = true
                        break
                    }
                    if (read == 0) continue
                    if (!pipeline.writeInputPcm(buffer, 0, read)) {
                        throw IllegalStateException("stt_pcm_write_failed")
                    }
                    total += read
                    endpoint = detector.acceptPcm16(buffer, 0, read)
                }
                lines += "telephony_rx_pcm_bytes=$total"
                lines += "endpoint_reason=${endpoint.reason?.name?.lowercase() ?: if (streamEnded) "stream_eof" else "cancelled"}"
                lines += "endpoint_capture_ms=${endpoint.capturedMs}"
                lines += "endpoint_speech_detected=${endpoint.speechDetected}"
                endpoint.estimatedSpeechEndMs?.let { speechEndMs ->
                    estimatedSpeechEndElapsedMs = captureStartedElapsedMs + speechEndMs
                    lines += "estimated_end_of_speech_elapsed_ms=$estimatedSpeechEndElapsedMs"
                }
                if (total < LocalSpeechFormat.bytesForDurationMs(500)) {
                    context.mainExecutor.execute { finish(false, "telephony_rx_too_short") }
                    return
                }
                pipeline.finishInput()
                lines += "stt_pcm_eof_sent=true"
                lines += "stt_eof_elapsed_ms=${elapsedTurnMs()}"
            } catch (error: Throwable) {
                context.mainExecutor.execute {
                    finish(false, "telephony_rx_${error.javaClass.simpleName}")
                }
            }
        }

        private fun writeOutputTurn(lease: CallMediaEndpointLease, pcm: ByteArray) {
            try {
                if (pcm.isEmpty()) throw IllegalStateException("tts_pcm_empty")
                val firstTxElapsedMs = elapsedTurnMs()
                lines += "first_tx_elapsed_ms=$firstTxElapsedMs"
                estimatedSpeechEndElapsedMs?.let { speechEndMs ->
                    lines += "end_of_speech_to_first_tx_ms=${(firstTxElapsedMs - speechEndMs).coerceAtLeast(0L)}"
                }
                lease.uplink().write(pcm)
                lease.uplink().flush()
                lines += "telephony_tx_pcm_bytes=${pcm.size}"
                lines += "turn_complete_elapsed_ms=${elapsedTurnMs()}"
                context.mainExecutor.execute { finish(true) }
            } catch (error: Throwable) {
                context.mainExecutor.execute {
                    finish(false, "telephony_tx_${error.javaClass.simpleName}")
                }
            }
        }

        private fun elapsedTurnMs(): Long =
            (SystemClock.elapsedRealtime() - turnStartedAtMs).coerceAtLeast(0L)

        private fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacks(timeout)
            try { pipeline.close() } catch (_: Throwable) {}
            activeLease = null
            try { mediaRuntime?.coordinator()?.takeOverNow() } catch (_: Throwable) {}
            try { mediaRuntime?.close() } catch (_: Throwable) {}
            mediaRuntime = null
            executor.shutdownNow()
            lines += "local_phone_llm_live_call_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            try { File(context.filesDir, REPORT_FILE).writeText(report) } catch (_: Throwable) {}
            context.mainExecutor.execute { callback(report) }
        }
    }

    private fun activeWorkflow(): CallWorkflow {
        val task = CallTask(
            "Orange test call",
            "conduct one non-committing test turn",
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
        "probe=local_phone_llm_live_call\n" +
            "local_phone_llm_live_call_success=false\n" +
            "failure_reason=$reason\n" +
            "probe_complete=true\n"

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
