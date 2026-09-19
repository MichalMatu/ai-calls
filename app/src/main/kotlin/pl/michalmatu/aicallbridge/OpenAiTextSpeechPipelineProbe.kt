package pl.michalmatu.aicallbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.textagent.BackendOpenAiTextBackend
import pl.michalmatu.aicallbridge.textagent.BackendOpenAiTextConfig
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy

/** Off-call proof: local S22 STT -> developer OpenAI text broker -> app approval -> local S22 TTS. */
internal object OpenAiTextSpeechPipelineProbe {
    const val REPORT_FILE = "openai-text-speech-pipeline-report.txt"
    private const val USER_TEXT = "Dzień dobry, proszę krótko się przywitać."
    private const val TIMEOUT_MS = 60_000L

    fun run(context: Context, config: OpenAiTextSmokeConfig, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        val lines = mutableListOf(
            "probe=openai_text_speech_pipeline",
            "call_required=false",
            "openai_api_used=true",
            "raw_audio_to_openai=false",
            "api_key_on_android=false",
            "backend_location=developer_https_broker",
            "approval_policy=application_owned",
            "timestamp_utc=${Instant.now()}",
            "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}",
        )
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val sourceTts = LocalTtsSpeechOutput(appContext)
        val backend = try {
            BackendOpenAiTextBackend(
                BackendOpenAiTextConfig(config.textEndpoint, config.brokerToken),
            )
        } catch (error: Throwable) {
            val report = (lines + listOf(
                "backend_config_valid=false",
                "failure_reason=${sanitize(error.javaClass.simpleName)}",
                "probe_complete=true",
            )).joinToString("\n") + "\n"
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
            return
        }
        lines += "backend_config_valid=true"
        val pipeline = LocalSpeechTextPipeline(
            appContext,
            backend,
            CallTextAgentOutputApprovalPolicy(
                activeDiagnosticWorkflow(),
                CallCommitmentGate { "diagnostic-openai-text-token" },
            ),
        )
        var transcriptAtMs: Long? = null

        fun elapsedMs(): Long = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { sourceTts.close() } catch (_: Throwable) {}
            try { pipeline.close() } catch (_: Throwable) {}
            lines += "openai_text_speech_pipeline_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
        }

        handler.postDelayed({ finish(false, "probe_timeout") }, TIMEOUT_MS)
        sourceTts.synthesize(USER_TEXT, object : LocalTtsSpeechOutput.Listener {
            override fun onPcm16Mono16k(pcm: ByteArray) {
                lines += "source_tts_pcm_bytes=${pcm.size}"
                pipeline.start(object : LocalSpeechTextPipeline.Listener {
                    override fun onSpeechInputReady() {
                        lines += "stt_ready=true"
                        Thread({
                            try {
                                val silence = ByteArray(LocalSpeechFormat.bytesForDurationMs(500))
                                val chunk = LocalSpeechFormat.bytesForDurationMs(20)
                                fun writePaced(data: ByteArray): Boolean {
                                    var offset = 0
                                    while (offset < data.size && !finished.get()) {
                                        val length = minOf(chunk, data.size - offset)
                                        if (!pipeline.writeInputPcm(data, offset, length)) return false
                                        offset += length
                                        Thread.sleep(20L)
                                    }
                                    return true
                                }
                                if (!writePaced(silence) || !writePaced(pcm) || !writePaced(silence)) {
                                    appContext.mainExecutor.execute { finish(false, "pcm_write_failed") }
                                    return@Thread
                                }
                                pipeline.finishInput()
                                lines += "pcm_eof_sent=true"
                            } catch (error: Throwable) {
                                appContext.mainExecutor.execute {
                                    finish(false, "stream_${error.javaClass.simpleName}")
                                }
                            }
                        }, "OpenAiTextPipelineInput").start()
                    }

                    override fun onUserTranscript(text: String) {
                        val elapsed = elapsedMs()
                        transcriptAtMs = elapsed
                        lines += "stt_text=${sanitize(text)}"
                        lines += "stt_transcript_nonblank=${text.isNotBlank()}"
                        lines += "stt_elapsed_ms=$elapsed"
                    }

                    override fun onApprovedText(text: String) {
                        val elapsed = elapsedMs()
                        lines += "backend_complete_response=true"
                        lines += "approved_text=${sanitize(text)}"
                        lines += "approved_text_nonblank=${text.isNotBlank()}"
                        lines += "approved_elapsed_ms=$elapsed"
                        transcriptAtMs?.let { lines += "transcript_to_approved_ms=${(elapsed - it).coerceAtLeast(0L)}" }
                    }

                    override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                        lines += "output_tts_pcm_bytes=${pcm.size}"
                        lines += "approved_output_pcm_nonempty=${pcm.isNotEmpty()}"
                        lines += "output_ready_elapsed_ms=${elapsedMs()}"
                        val transcriptOk = lines.any { it == "stt_transcript_nonblank=true" }
                        val responseOk = lines.any { it == "approved_text_nonblank=true" }
                        finish(transcriptOk && responseOk && pcm.isNotEmpty())
                    }

                    override fun onDroppedText() = finish(false, "unexpected_output_drop")
                    override fun onError(reason: String) = finish(false, reason)
                })
            }

            override fun onError(reason: String) = finish(false, "source_tts_$reason")
        })
    }

    private fun activeDiagnosticWorkflow(): CallWorkflow {
        val task = CallTask(
            "diagnostic target",
            "diagnostic action",
            "diagnostic service",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
        workflow.resolveTarget(CallResolvedTarget("diagnostic", "000"))
        workflow.markDialing()
        workflow.markCallActive()
        return workflow
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
