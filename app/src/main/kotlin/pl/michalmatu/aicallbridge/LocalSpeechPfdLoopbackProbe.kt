package pl.michalmatu.aicallbridge

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechPcm
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Off-call end-to-end proof for local TTS -> PCM16/16 kHz -> PFD -> on-device STT. */
@TargetApi(34)
object LocalSpeechPfdLoopbackProbe {
    private const val LANGUAGE_TAG = "pl-PL"
    private const val REPORT_FILE_NAME = "local-speech-pfd-loopback-report.txt"
    private const val TEST_TEXT = "To jest test lokalnego rozpoznawania mowy."
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_MS = 30_000L
    private const val TTS_ID = "local-speech-pfd-loopback"

    fun run(context: Context, callback: (String) -> Unit) {
        val appContext = context.applicationContext
        val lines = mutableListOf<String>()
        val finished = AtomicBoolean(false)
        val timeoutHandler = Handler(Looper.getMainLooper())
        var activeRecognizer: SpeechRecognizer? = null
        var activePfd: ParcelFileDescriptor? = null
        var activeWriterPfd: ParcelFileDescriptor? = null
        var activeTts: TextToSpeech? = null
        val recognizedSegments = mutableListOf<String>()

        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            timeoutHandler.removeCallbacksAndMessages(null)
            try { activeRecognizer?.cancel() } catch (_: Throwable) {}
            try { activeRecognizer?.destroy() } catch (_: Throwable) {}
            try { activeWriterPfd?.close() } catch (_: Throwable) {}
            try { activePfd?.close() } catch (_: Throwable) {}
            try { activeTts?.shutdown() } catch (_: Throwable) {}
            lines += "probe_complete=true"
            val report = lines.joinToString("\n") + "\n"
            File(appContext.filesDir, REPORT_FILE_NAME).writeText(report)
            callback(report)
        }

        fun fail(reason: String) {
            lines += "loopback_success=false"
            lines += "failure_reason=${sanitize(reason)}"
            finish()
        }

        lines += "probe_version=1"
        lines += "timestamp_utc=${Instant.now()}"
        lines += "sdk=${android.os.Build.VERSION.SDK_INT}"
        lines += "language=$LANGUAGE_TAG"
        lines += "test_text=${sanitize(TEST_TEXT)}"
        lines += "target_pcm=mono,pcm16,$TARGET_SAMPLE_RATE"
        lines += "record_audio_permission=${context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED}"

        if (android.os.Build.VERSION.SDK_INT < 34) {
            fail("api_below_34")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("record_audio_permission_missing")
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            fail("on_device_recognizer_unavailable")
            return
        }

        timeoutHandler.postDelayed({ fail("probe_timeout") }, TIMEOUT_MS)

        fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_TAG)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        fun startRecognition(rawFile: File) {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            activeRecognizer = recognizer
            val pipe = ParcelFileDescriptor.createPipe()
            val readPfd = pipe[0]
            val writePfd = pipe[1]
            activePfd = readPfd
            activeWriterPfd = writePfd
            val request = recognitionIntent().apply {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readPfd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, TARGET_SAMPLE_RATE)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }

            fun transcriptSuccess(value: String): Boolean {
                val normalized = normalize(value)
                return normalized.contains("test") &&
                    normalized.contains("lokal") &&
                    normalized.contains("rozpozn")
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { lines += "stt_ready=true" }
                override fun onBeginningOfSpeech() { lines += "stt_beginning=true" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { lines += "stt_end_of_speech=true" }
                override fun onError(error: Int) { fail("stt_error_$error") }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onSegmentResults(segmentResults: Bundle) {
                    val hypotheses = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val top = hypotheses.firstOrNull().orEmpty()
                    lines += "stt_segment_result_count=${hypotheses.size}"
                    lines += "stt_segment_top=${sanitize(top)}"
                    if (top.isNotBlank()) recognizedSegments += top
                }

                override fun onEndOfSegmentedSession() {
                    lines += "stt_segmented_session_end=true"
                    val transcript = recognizedSegments.joinToString(" ")
                    lines += "stt_top=${sanitize(transcript)}"
                    val success = transcriptSuccess(transcript)
                    lines += "loopback_success=$success"
                    if (!success) lines += "failure_reason=unexpected_transcript"
                    finish()
                }

                override fun onResults(results: Bundle?) {
                    val hypotheses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val top = hypotheses.firstOrNull().orEmpty()
                    lines += "stt_result_count=${hypotheses.size}"
                    lines += "stt_top=${sanitize(top)}"
                    val success = transcriptSuccess(top)
                    lines += "loopback_success=$success"
                    if (!success) lines += "failure_reason=unexpected_transcript"
                    finish()
                }
            })

            try {
                recognizer.startListening(request)
                lines += "stt_start_listening=true"
            } catch (error: Throwable) {
                fail("stt_start_${error.javaClass.simpleName}")
                return
            }

            val raw = rawFile.readBytes()
            Thread({
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { output ->
                        val chunkBytes = TARGET_SAMPLE_RATE * 2 * 20 / 1000
                        val silence = ByteArray(TARGET_SAMPLE_RATE * 2 / 2)

                        fun writePaced(data: ByteArray) {
                            var offset = 0
                            while (offset < data.size && !finished.get()) {
                                val size = minOf(chunkBytes, data.size - offset)
                                output.write(data, offset, size)
                                output.flush()
                                offset += size
                                Thread.sleep(20)
                            }
                        }

                        writePaced(silence)
                        writePaced(raw)
                        writePaced(silence)
                    }
                    activeWriterPfd = null
                } catch (error: Throwable) {
                    appContext.mainExecutor.execute {
                        if (!finished.get()) fail("pcm_pipe_${error.javaClass.simpleName}")
                    }
                }
            }, "LocalSpeechPcmPipe").start()
        }

        fun synthesizeAndRecognize() {
            val holder = arrayOfNulls<TextToSpeech>(1)
            holder[0] = TextToSpeech(appContext) { status ->
                appContext.mainExecutor.execute {
                    val tts = holder[0]
                    activeTts = tts
                    lines += "tts_init_status=$status"
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        fail("tts_init_failed")
                        return@execute
                    }
                    val localVoice = tts.voices.orEmpty()
                        .filter { it.locale.language.equals("pl", ignoreCase = true) && !it.isNetworkConnectionRequired }
                        .sortedBy { it.name }
                        .firstOrNull()
                    if (localVoice == null) {
                        fail("local_polish_tts_voice_missing")
                        return@execute
                    }
                    lines += "tts_voice=${sanitize(localVoice.name)}"
                    lines += "tts_voice_network_required=${localVoice.isNetworkConnectionRequired}"
                    if (tts.setVoice(localVoice) != TextToSpeech.SUCCESS) {
                        fail("tts_set_voice_failed")
                        return@execute
                    }

                    val waveFile = File(appContext.cacheDir, "local-speech-pfd-loopback.wav").apply { delete() }
                    val rawFile = File(appContext.cacheDir, "local-speech-pfd-loopback.pcm").apply { delete() }
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            appContext.mainExecutor.execute {
                                try {
                                    val decoded = LocalSpeechPcm.decodeWaveToMonoPcm16(waveFile.readBytes())
                                    val target = LocalSpeechPcm.resampleLinear(
                                        decoded.monoSamples,
                                        decoded.sampleRate,
                                        TARGET_SAMPLE_RATE,
                                    )
                                    val raw = LocalSpeechPcm.toLittleEndianBytes(target)
                                    rawFile.writeBytes(raw)
                                    lines += "tts_wave_sample_rate=${decoded.sampleRate}"
                                    lines += "tts_wave_channels=${decoded.sourceChannelCount}"
                                    lines += "tts_wave_mono_samples=${decoded.monoSamples.size}"
                                    lines += "pcm16_16k_samples=${target.size}"
                                    lines += "pcm16_16k_bytes=${raw.size}"
                                    lines += "pcm16_16k_nonempty=${raw.isNotEmpty()}"
                                    try { tts.shutdown() } catch (_: Throwable) {}
                                    activeTts = null
                                    waveFile.delete()
                                    startRecognition(rawFile)
                                } catch (error: Throwable) {
                                    fail("pcm_prepare_${error.message ?: error.javaClass.simpleName}")
                                }
                            }
                        }

                        @Deprecated("Deprecated by framework")
                        override fun onError(utteranceId: String?) { fail("tts_synthesis_error_legacy") }
                        override fun onError(utteranceId: String?, errorCode: Int) { fail("tts_synthesis_error_$errorCode") }
                    })
                    val queued = tts.synthesizeToFile(TEST_TEXT, Bundle.EMPTY, waveFile, TTS_ID)
                    lines += "tts_synthesize_queue_result=$queued"
                    if (queued != TextToSpeech.SUCCESS) fail("tts_synthesis_queue_failed")
                }
            }
        }

        val supportRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        activeRecognizer = supportRecognizer
        supportRecognizer.checkRecognitionSupport(
            recognitionIntent(),
            appContext.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    val installed = recognitionSupport.installedOnDeviceLanguages.any(::isPolish)
                    val downloadable = recognitionSupport.supportedOnDeviceLanguages.any(::isPolish)
                    lines += "stt_pl_installed_before=$installed"
                    lines += "stt_pl_downloadable=$downloadable"
                    if (installed) {
                        supportRecognizer.destroy()
                        activeRecognizer = null
                        synthesizeAndRecognize()
                        return
                    }
                    if (!downloadable) {
                        fail("polish_model_not_downloadable")
                        return
                    }
                    lines += "model_download_requested=true"
                    supportRecognizer.triggerModelDownload(
                        recognitionIntent(),
                        appContext.mainExecutor,
                        object : ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {
                                lines += "model_download_progress=$completedPercent"
                            }

                            override fun onSuccess() {
                                lines += "model_download_success=true"
                                supportRecognizer.destroy()
                                activeRecognizer = null
                                synthesizeAndRecognize()
                            }

                            override fun onScheduled() {
                                lines += "model_download_scheduled=true"
                                lines += "loopback_success=false"
                                lines += "failure_reason=model_download_scheduled"
                                finish()
                            }

                            override fun onError(error: Int) {
                                fail("model_download_error_$error")
                            }
                        },
                    )
                }

                override fun onError(error: Int) { fail("support_check_error_$error") }
            },
        )
    }

    private fun isPolish(tag: String): Boolean = Locale.forLanguageTag(tag).language.equals("pl", ignoreCase = true)

    private fun normalize(value: String): String = value
        .lowercase(Locale.forLanguageTag(LANGUAGE_TAG))
        .replace(Regex("[^a-ząćęłńóśźż0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sanitize(value: String): String = value.replace('\n', ' ').replace('\r', ' ').replace('=', ':')
}
