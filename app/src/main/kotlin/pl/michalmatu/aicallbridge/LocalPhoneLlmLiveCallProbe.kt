package pl.michalmatu.aicallbridge

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPlanAction
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localcall.AndroidLocalTextCallBackendFactory
import pl.michalmatu.aicallbridge.localcall.LocalTextCallSession
import pl.michalmatu.aicallbridge.localcall.PreparedLocalTextCall
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.PcmEndOfUtteranceDetector
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.session.CallMediaEndpointLease
import pl.michalmatu.aicallbridge.session.CallMediaSessionRuntime
import pl.michalmatu.aicallbridge.session.CallMediaSessionSnapshot
import pl.michalmatu.aicallbridge.session.CallMediaSessionState
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * One bounded live cellular turn. Legacy providers use the neutral LocalTextCallSession path;
 * Gate C binds PhraseMatrix + CallPlan and a fail-closed backend sentinel before final STT routing.
 */
internal object LocalPhoneLlmLiveCallProbe {
    private const val REPORT_FILE = "local-phone-llm-live-call-report.txt"
    private const val TIMEOUT_MS = 90_000L

    fun run(context: Context, callback: (String) -> Unit) =
        run(
            context,
            LocalPhoneLlmLiveCallProbeRequest.create(
                provider = TextLlmProvider.LOCAL_PHONE_LLM,
                gateCFastPath = false,
                liveCallTarget = null,
            ),
            callback,
        )

    fun run(
        context: Context,
        provider: TextLlmProvider,
        callback: (String) -> Unit,
    ) = run(
        context,
        LocalPhoneLlmLiveCallProbeRequest.create(
            provider = provider,
            gateCFastPath = false,
            liveCallTarget = null,
        ),
        callback,
    )

    fun run(
        context: Context,
        request: LocalPhoneLlmLiveCallProbeRequest,
        callback: (String) -> Unit,
    ) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null || audioManager.mode != AudioManager.MODE_IN_CALL) {
            callback(immediateReport("cellular_call_not_active", request))
            return
        }
        Run(appContext, request, callback).start()
    }

    private class Run(
        private val context: Context,
        private val request: LocalPhoneLlmLiveCallProbeRequest,
        private val callback: (String) -> Unit,
    ) {
        private val provider = request.provider
        private val handler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)
        private val mediaTurnStarted = AtomicBoolean(false)
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LocalPhoneLlmLiveTurn").apply { isDaemon = true }
        }
        private val fastPath: GateCLiveCallFastPath? = if (request.gateCFastPath) {
            GateCLiveCallFastPathFactory.create(
                checkNotNull(request.liveCallTarget),
                checkNotNull(request.orangeLiveAction),
            )
        } else {
            null
        }
        private val workflow: CallWorkflow = fastPath?.scenario?.workflow?.also { workflow ->
            workflow.markDialing()
            workflow.markCallActive()
        } ?: activeWorkflow()
        private val backend = fastPath?.backend ?: AndroidLocalTextCallBackendFactory.create(context, provider)
        private val approvalPolicy = CallTextAgentOutputApprovalPolicy(
            workflow,
            CallCommitmentGate { "live-local-phone-llm-token" },
        )
        private val prepared = PreparedLocalTextCall(
            workflow = workflow,
            backend = backend,
            callPlan = fastPath?.scenario?.callPlan,
            phraseMatrix = fastPath?.scenario?.phraseMatrix,
        )
        private val session = LocalTextCallSession.create(
            context = context,
            prepared = prepared,
            approvalPolicy = approvalPolicy,
        )
        private val lines = mutableListOf(
            "probe=local_phone_llm_live_call",
            "call_required=true",
            "backend_location=${if (request.gateCFastPath) "gate_c_sentinel" else "phone_loopback"}",
            "text_llm_provider=${provider.name}",
            "gate_c_fast_path=${request.gateCFastPath}",
            "gate_c_call_plan_bound=${fastPath != null}",
            "orange_live_action=${request.orangeLiveAction?.wireId ?: "none"}",
            "approval_policy=application_owned",
            "endpointing=trailing_silence",
            "rx_max_capture_ms=${fastPath?.maxCaptureMs ?: 60_000}",
            "timestamp_utc=${Instant.now()}",
            "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}",
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

            val listener = object : LocalSpeechTextPipeline.Listener {
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
                    if (request.gateCFastPath) {
                        lines += "backend_complete_response=false"
                        lines += "approved_text_source=call_plan_candidate"
                    } else {
                        lines += "backend_complete_response=true"
                        lines += "approved_text_source=backend"
                    }
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
            }

            if (request.gateCFastPath) {
                session.startWithPlanRouting(
                    listener,
                    LocalTextCallSession.StructuredPlanTurnListener { action, ruleId ->
                        lines += "call_plan_action=${action.name.lowercase()}"
                        ruleId?.let { lines += "call_plan_rule_id=${sanitize(it)}" }
                        if (action != CallPlanAction.SAY) {
                            context.mainExecutor.execute {
                                finish(false, "gate_c_${action.name.lowercase()}")
                            }
                        }
                    },
                )
            } else {
                session.start(listener)
            }
        }

        private fun captureInputTurn(lease: CallMediaEndpointLease) {
            val detector = PcmEndOfUtteranceDetector(
                trailingSilenceMs = 1_500,
                maxCaptureMs = fastPath?.maxCaptureMs ?: 60_000,
            )
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
                    if (!session.writeInputPcm(buffer, 0, read)) {
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
                session.finishInput()
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
            try { session.close() } catch (_: Throwable) {}
            activeLease = null
            try { mediaRuntime?.coordinator()?.takeOverNow() } catch (_: Throwable) {}
            try { mediaRuntime?.close() } catch (_: Throwable) {}
            mediaRuntime = null
            executor.shutdownNow()
            fastPath?.backend?.let { lines += "backend_generate_calls=${it.generateCalls}" }
            lines += "local_text_llm_live_call_success=$success"
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

    private fun immediateReport(
        reason: String,
        request: LocalPhoneLlmLiveCallProbeRequest,
    ): String =
        "probe=local_phone_llm_live_call\n" +
            "text_llm_provider=${request.provider.name}\n" +
            "gate_c_fast_path=${request.gateCFastPath}\n" +
            "gate_c_call_plan_bound=false\n" +
            "orange_live_action=${request.orangeLiveAction?.wireId ?: "none"}\n" +
            "local_text_llm_live_call_success=false\n" +
            "local_phone_llm_live_call_success=false\n" +
            "failure_reason=$reason\n" +
            "probe_complete=true\n"

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
