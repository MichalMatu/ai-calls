package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import pl.michalmatu.aicallbridge.LocalSpeechTextPipelineProbe
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/**
 * Explicit DUMP-gated off-call integration of the real Android Polish STT,
 * real app-owned Gemma E2B LiteRT, product-owned text approval and local TTS.
 * Input is generated entirely by local TTS. No user audio, raw transcripts or
 * model replies are persisted; no SIP, GSM capture, telephone or network API.
 *
 * A single bounded turn only. Later VoIP owns actual SRTP and media leasing.
 */
class S8NativeSpeechSmokeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var completed = false
    private var source: LocalTtsSpeechOutput? = null
    private var pipeline: LocalSpeechTextPipeline? = null
    private var feeder: Thread? = null
    private var sttMatched = false
    private var textApproved = false
    private var ttsPcmReady = false
    @Volatile
    private var inputEnded = false
    private val timeout = Runnable { complete("timeout") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        report("running")
        val telecom = getSystemService(TelecomManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        val idle = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED &&
            telecom != null && !telecom.isInCall && !telecom.isInManagedCall &&
            audio != null && audio.mode != AudioManager.MODE_IN_CALL &&
            audio.mode != AudioManager.MODE_IN_COMMUNICATION
        if (!idle) {
            complete("blocked_not_idle_or_permission")
            return
        }
        val ready = runCatching {
            AndroidGemma4ModelReadiness.check(this, DialogueModelId.GEMMA_4_E2B).state ==
                Gemma4ModelReadinessState.READY
        }.getOrDefault(false)
        if (!ready) {
            complete("model_unavailable")
            return
        }
        try {
            val backend = Gemma4LiteRtTextBackendFactory.create(
                applicationContext,
                systemInstruction = "Jesteś lokalnym asystentem testowym. " +
                    "Odpowiedz tylko jednym krótkim polskim zdaniem. " +
                    "Bez narzędzi, połączeń i danych osobowych.",
                modelId = DialogueModelId.GEMMA_4_E2B,
            )
            pipeline = LocalSpeechTextPipeline(
                applicationContext,
                backend,
                CallTextAgentOutputApprovalPolicy(
                    LocalSpeechTextPipelineProbe.activeDiagnosticWorkflow(),
                    CallCommitmentGate { "s8-synthetic-local-only" },
                ),
            )
            source = LocalTtsSpeechOutput(applicationContext)
            handler.postDelayed(timeout, MAX_WAIT_MS)
            source?.synthesize(SYNTHETIC_PHRASE, object : LocalTtsSpeechOutput.Listener {
                override fun onPcm16Mono16k(pcm: ByteArray) {
                    if (completed) {
                        pcm.fill(0)
                        return
                    }
                    startListening(pcm)
                }

                override fun onError(reason: String) {
                    complete("source_tts_failed")
                }
            })
        } catch (_: Throwable) {
            complete("native_setup_failed")
        }
    }

    private fun startListening(sourcePcm: ByteArray) {
        val turn = pipeline ?: run {
            sourcePcm.fill(0)
            complete("pipeline_missing")
            return
        }
        try {
            turn.start(object : LocalSpeechTextPipeline.Listener {
                override fun onSpeechInputReady() {
                    if (completed) {
                        sourcePcm.fill(0)
                        return
                    }
                    val started = Thread({ feedAudio(turn, sourcePcm) }, "s8-native-speech-feed")
                        .apply { isDaemon = true }
                    feeder = started
                    started.start()
                }

                override fun onUserTranscript(text: String) {
                    val normalized = text.lowercase(java.util.Locale.forLanguageTag("pl-PL"))
                    sttMatched = normalized.contains("lokalnego") &&
                        normalized.contains("agenta")
                }

                override fun onApprovedText(text: String) {
                    textApproved = text.isNotBlank()
                }

                override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                    ttsPcmReady = pcm.isNotEmpty() && pcm.size % 2 == 0
                    pcm.fill(0)
                    complete(if (sttMatched && textApproved && ttsPcmReady && inputEnded)
                        "pass" else "e2e_incomplete")
                }

                override fun onDroppedText() = complete("approval_drop")
                override fun onError(reason: String) = complete("pipeline_failed")
            })
        } catch (_: Throwable) {
            sourcePcm.fill(0)
            complete("stt_start_failed")
        }
    }

    private fun feedAudio(turn: LocalSpeechTextPipeline, audioPcm: ByteArray) {
        try {
            val silence = ByteArray(LocalSpeechFormat.bytesForDurationMs(500))
            val frame = LocalSpeechFormat.bytesForDurationMs(20)
            fun paced(bytes: ByteArray): Boolean {
                var offset = 0
                while (offset < bytes.size && !completed) {
                    val size = minOf(frame, bytes.size - offset)
                    if (!turn.writeInputPcm(bytes, offset, size)) return false
                    offset += size
                    Thread.sleep(20L)
                }
                return !completed
            }
            if (!paced(silence) || !paced(audioPcm) || !paced(silence)) {
                handler.post { complete("audio_feed_failed") }
                return
            }
            inputEnded = true
            turn.finishInput()
        } catch (_: Throwable) {
            handler.post { complete("audio_feed_failed") }
        } finally {
            audioPcm.fill(0)
        }
    }

    private fun complete(status: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { complete(status) }
            return
        }
        if (completed) return
        completed = true
        handler.removeCallbacks(timeout)
        feeder?.interrupt()
        runCatching { source?.close() }
        runCatching { pipeline?.close() }
        source = null
        pipeline = null
        report(status)
        finish()
    }

    private fun report(status: String) {
        PrivateDiagnosticReportStore.write(filesDir, REPORT_FILE, buildString {
            appendLine("probe=s8_native_speech_one_turn")
            appendLine("backend=on_device_gemma_e2b")
            appendLine("stt_matched=$sttMatched")
            appendLine("text_approved=$textApproved")
            appendLine("tts_pcm_ready=$ttsPcmReady")
            appendLine("input_ended=$inputEnded")
            appendLine("status=$status")
            appendLine("real_call=false")
            appendLine("remote_model=false")
            appendLine("report_complete=${status != "running"}")
        })
    }

    override fun onStop() {
        if (!completed) complete("stopped")
        super.onStop()
    }

    companion object {
        const val REPORT_FILE = "s8-native-speech-one-turn-report.txt"
        private const val MAX_WAIT_MS = 180_000L
        private const val SYNTHETIC_PHRASE = "To jest test lokalnego agenta tekstowego."
    }
}
