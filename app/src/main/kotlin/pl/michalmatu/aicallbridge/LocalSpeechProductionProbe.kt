package pl.michalmatu.aicallbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechFormat
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.localspeech.OnDeviceSpeechInput
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Off-call proof that production local speech adapters round-trip on the exact S22. */
object LocalSpeechProductionProbe {
    private const val TEST_TEXT = "To jest test lokalnego rozpoznawania mowy."
    private const val REPORT_FILE = "local-speech-production-report.txt"
    private const val TIMEOUT_MS = 30_000L

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val input = OnDeviceSpeechInput(appContext)
        val output = LocalTtsSpeechOutput(appContext)
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        val lines = mutableListOf<String>()

        fun finish(success: Boolean, reason: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            input.close()
            output.close()
            lines += "production_roundtrip_success=$success"
            if (reason != null) lines += "failure_reason=$reason"
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            File(appContext.filesDir, REPORT_FILE).writeText(report)
            callback(report)
        }

        lines += "probe_version=1"
        lines += "timestamp_utc=${Instant.now()}"
        lines += "test_text=$TEST_TEXT"
        lines += "target_pcm=mono,pcm16,${LocalSpeechFormat.SAMPLE_RATE_HZ}"
        handler.postDelayed({ finish(false, "probe_timeout") }, TIMEOUT_MS)

        output.synthesize(TEST_TEXT, object : LocalTtsSpeechOutput.Listener {
            override fun onPcm16Mono16k(pcm: ByteArray) {
                lines += "tts_pcm_bytes=${pcm.size}"
                input.start(object : OnDeviceSpeechInput.Listener {
                    override fun onReady() {
                        lines += "stt_ready=true"
                        Thread {
                            try {
                                val silence = ByteArray(LocalSpeechFormat.bytesForDurationMs(500))
                                val chunk = LocalSpeechFormat.bytesForDurationMs(20)
                                input.writePcm(silence)
                                var offset = 0
                                while (offset < pcm.size) {
                                    val length = minOf(chunk, pcm.size - offset)
                                    if (!input.writePcm(pcm, offset, length)) {
                                        appContext.mainExecutor.execute { finish(false, "pcm_write_failed") }
                                        return@Thread
                                    }
                                    offset += length
                                    Thread.sleep(20L)
                                }
                                input.writePcm(silence)
                                input.finishInput()
                                lines += "pcm_eof_sent=true"
                            } catch (error: Throwable) {
                                appContext.mainExecutor.execute { finish(false, "stream_${error.javaClass.simpleName}") }
                            }
                        }.start()
                    }

                    override fun onFinalTranscript(text: String) {
                        lines += "stt_text=$text"
                        val normalized = normalize(text)
                        val success = normalized.contains("test") &&
                            normalized.contains("lokal") &&
                            normalized.contains("rozpozn")
                        finish(success, if (success) null else "unexpected_transcript")
                    }

                    override fun onError(reason: String) {
                        finish(false, "stt_$reason")
                    }
                })
            }

            override fun onError(reason: String) {
                finish(false, "tts_$reason")
            }
        })
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.forLanguageTag("pl-PL"))
        .replace(Regex("[^a-ząćęłńóśźż0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
