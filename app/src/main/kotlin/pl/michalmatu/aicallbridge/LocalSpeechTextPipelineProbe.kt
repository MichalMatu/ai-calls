package pl.michalmatu.aicallbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Off-call proof of FINAL STT -> complete text backend -> app approval -> local TTS. */
object LocalSpeechTextPipelineProbe {
    private const val USER_TEXT = "To jest test lokalnego agenta tekstowego."
    private const val RESPONSE_TEXT = "Lokalny agent tekstowy działa poprawnie."
    private const val REPORT_FILE = "local-speech-text-pipeline-report.txt"
    private const val TIMEOUT_MS = 40_000L

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val lines = mutableListOf<String>()
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val sourceTts = LocalTtsSpeechOutput(appContext)
        val backend = DeterministicBackend(appContext)
        val workflow = activeDiagnosticWorkflow()
        val commitmentGate = CallCommitmentGate { "diagnostic-text-token" }
        val approval = CallTextAgentOutputApprovalPolicy(workflow, commitmentGate)
        val pipeline = LocalSpeechTextPipeline(appContext, backend, approval)

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { sourceTts.close() } catch (_: Throwable) {}
            try { pipeline.close() } catch (_: Throwable) {}
            lines += "text_pipeline_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
        }

        lines += "probe_version=1"
        lines += "timestamp_utc=${Instant.now()}"
        lines += "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}"
        lines += "approval_policy=application_owned"
        lines += "backend_mode=deterministic_complete_text"
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
                        }, "LocalTextPipelineInput").start()
                    }

                    override fun onUserTranscript(text: String) {
                        lines += "stt_text=${sanitize(text)}"
                    }

                    override fun onApprovedText(text: String) {
                        lines += "approved_text=${sanitize(text)}"
                    }

                    override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                        lines += "output_tts_pcm_bytes=${pcm.size}"
                        val transcriptOk = normalize(backend.lastUserText).contains("lokalnego agenta tekstowego")
                        val responseOk = backend.lastResponse == RESPONSE_TEXT
                        val pcmOk = pcm.isNotEmpty()
                        lines += "backend_received_transcript=$transcriptOk"
                        lines += "backend_complete_response=$responseOk"
                        lines += "approved_output_pcm_nonempty=$pcmOk"
                        finish(transcriptOk && responseOk && pcmOk)
                    }

                    override fun onDroppedText() {
                        finish(false, "unexpected_output_drop")
                    }

                    override fun onError(reason: String) {
                        finish(false, reason)
                    }
                })
            }

            override fun onError(reason: String) {
                finish(false, "source_tts_$reason")
            }
        })
    }

    private class DeterministicBackend(
        private val context: Context,
    ) : TextCallAgentBackend {
        @Volatile
        var lastUserText: String = ""
            private set
        @Volatile
        var lastResponse: String = ""
            private set
        @Volatile
        private var generation = 0L

        @Synchronized
        override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
            generation += 1L
            val current = generation
            lastUserText = userText
            context.mainExecutor.execute {
                synchronized(this) {
                    if (generation != current) return@execute
                    lastResponse = RESPONSE_TEXT
                }
                listener.onComplete(RESPONSE_TEXT)
            }
        }

        @Synchronized
        override fun cancel() {
            generation += 1L
        }
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

    private fun normalize(value: String): String = value
        .lowercase(Locale.forLanguageTag("pl-PL"))
        .replace(Regex("[^a-ząćęłńóśźż0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
