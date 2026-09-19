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
import pl.michalmatu.aicallbridge.localcall.DialTargetAuthorization
import pl.michalmatu.aicallbridge.localcall.LocalPhoneTextCallReadiness
import pl.michalmatu.aicallbridge.localcall.LocalTextCallReadinessCoordinator
import pl.michalmatu.aicallbridge.localcall.LocalTextCallSession
import pl.michalmatu.aicallbridge.localcall.PreparedLocalTextCall
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Off-call proof of READY_TO_DIAL -> local S22 STT -> phone LLM -> approval -> local S22 TTS. */
internal object LocalPhoneLlmSpeechPipelineProbe {
    private const val USER_TEXT = "To jest test lokalnego modelu na telefonie."
    private const val REPORT_FILE = "local-phone-llm-speech-pipeline-report.txt"
    private const val TIMEOUT_MS = 180_000L

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val lines = mutableListOf(
            "probe=local_phone_llm_speech_pipeline",
            "call_required=false",
            "openai_api_used=false",
            "backend_location=phone_loopback",
            "approval_policy=application_owned",
            "timestamp_utc=${Instant.now()}",
            "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}",
        )
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val sourceTts = LocalTtsSpeechOutput(appContext)
        val workflow = readyDiagnosticWorkflow()
        val readiness = LocalPhoneTextCallReadiness.create(
            appContext,
            workflow,
            DialTargetAuthorization { target -> target.dialAddress() == "000" },
        )
        var prepared: PreparedLocalTextCall? = null
        var session: LocalTextCallSession? = null

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            try { sourceTts.close() } catch (_: Throwable) {}
            try { session?.close() } catch (_: Throwable) {}
            try { prepared?.close() } catch (_: Throwable) {}
            try { readiness.close() } catch (_: Throwable) {}
            lines += "local_phone_llm_speech_pipeline_success=$success"
            if (reason != null) lines += "failure_reason=${sanitize(reason)}"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
        }

        fun runPreparedPipeline(ready: PreparedLocalTextCall) {
            if (finished.get()) {
                ready.close()
                return
            }
            prepared = ready
            lines += "readiness_state=READY_TO_DIAL"
            lines += "ready_to_dial=true"
            lines += "backend_config_valid=true"
            lines += "model_warmup_complete=true"

            try {
                workflow.markDialing()
                workflow.markCallActive()
            } catch (error: Throwable) {
                finish(false, "workflow_activate_${error.javaClass.simpleName}")
                return
            }

            val approval = CallTextAgentOutputApprovalPolicy(
                workflow,
                CallCommitmentGate { "diagnostic-phone-llm-token" },
            )
            val activeSession = try {
                LocalTextCallSession.create(appContext, ready, approval)
            } catch (error: Throwable) {
                finish(false, "session_create_${error.javaClass.simpleName}")
                return
            }
            session = activeSession

            sourceTts.synthesize(USER_TEXT, object : LocalTtsSpeechOutput.Listener {
                override fun onPcm16Mono16k(pcm: ByteArray) {
                    lines += "source_tts_pcm_bytes=${pcm.size}"
                    activeSession.start(object : LocalSpeechTextPipeline.Listener {
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
                                            if (!activeSession.writeInputPcm(data, offset, length)) return false
                                            offset += length
                                            Thread.sleep(20L)
                                        }
                                        return true
                                    }
                                    if (!writePaced(silence) || !writePaced(pcm) || !writePaced(silence)) {
                                        appContext.mainExecutor.execute { finish(false, "pcm_write_failed") }
                                        return@Thread
                                    }
                                    activeSession.finishInput()
                                    lines += "pcm_eof_sent=true"
                                } catch (error: Throwable) {
                                    appContext.mainExecutor.execute { finish(false, "stream_${error.javaClass.simpleName}") }
                                }
                            }, "LocalPhoneLlmPipelineInput").start()
                        }

                        override fun onUserTranscript(text: String) {
                            lines += "stt_text=${sanitize(text)}"
                            lines += "stt_transcript_nonblank=${text.isNotBlank()}"
                        }

                        override fun onApprovedText(text: String) {
                            lines += "backend_complete_response=true"
                            lines += "approved_text=${sanitize(text)}"
                            lines += "approved_text_nonblank=${text.isNotBlank()}"
                        }

                        override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                            lines += "output_tts_pcm_bytes=${pcm.size}"
                            val pcmOk = pcm.isNotEmpty()
                            lines += "approved_output_pcm_nonempty=$pcmOk"
                            val transcriptOk = lines.any { it == "stt_transcript_nonblank=true" }
                            val responseOk = lines.any { it == "approved_text_nonblank=true" }
                            finish(transcriptOk && responseOk && pcmOk)
                        }

                        override fun onDroppedText() = finish(false, "unexpected_output_drop")
                        override fun onError(reason: String) = finish(false, reason)
                    })
                }

                override fun onError(reason: String) = finish(false, "source_tts_$reason")
            })
        }

        handler.postDelayed({ finish(false, "probe_timeout") }, TIMEOUT_MS)
        readiness.prepare(object : LocalTextCallReadinessCoordinator.Listener {
            override fun onReady(prepared: PreparedLocalTextCall) {
                appContext.mainExecutor.execute { runPreparedPipeline(prepared) }
            }

            override fun onFailure(reason: String) {
                appContext.mainExecutor.execute {
                    lines += "ready_to_dial=false"
                    finish(false, "readiness_$reason")
                }
            }
        })
    }

    private fun readyDiagnosticWorkflow(): CallWorkflow {
        val task = CallTask(
            "diagnostic target",
            "diagnostic action",
            "diagnostic service",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        return CallWorkflow(task, CallConfirmationPolicy()) { }.apply {
            resolveTarget(CallResolvedTarget("diagnostic", "000"))
        }
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(240)
}
