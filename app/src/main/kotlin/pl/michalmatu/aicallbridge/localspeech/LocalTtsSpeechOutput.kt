package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID

internal class LocalTtsSpeechOutput(
    context: Context,
    private val languageTag: String = "pl-PL",
    private val voiceId: String? = null,
) : AutoCloseable {
    interface Listener {
        fun onPcm16Mono16k(pcm: ByteArray)
        fun onError(reason: String)
    }

    private val appContext = context.applicationContext
    private val gate = LocalSpeechGenerationGate()
    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var outputFile: File? = null

    fun synthesize(text: String, listener: Listener) {
        require(text.isNotBlank()) { "text_must_not_be_blank" }
        cancel()
        val generation = gate.begin()
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(appContext) { status ->
            appContext.mainExecutor.execute {
                if (!gate.isCurrent(generation)) {
                    try { holder[0]?.shutdown() } catch (_: Throwable) {}
                    return@execute
                }
                val tts = holder[0]
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    finishWithError(generation, listener, "tts_init_failed")
                    return@execute
                }
                synchronized(lock) { engine = tts }
                val selectedLanguage = Locale.forLanguageTag(languageTag).language
                val localVoice = tts.voices.orEmpty()
                    .filter { it.locale.language.equals(selectedLanguage, ignoreCase = true) && !it.isNetworkConnectionRequired }
                    .filter { voiceId == null || it.name == voiceId }
                    .sortedBy { it.name }
                    .firstOrNull()
                if (localVoice == null) {
                    finishWithError(generation, listener, "local_voice_missing")
                    return@execute
                }
                if (tts.setVoice(localVoice) != TextToSpeech.SUCCESS) {
                    finishWithError(generation, listener, "tts_set_voice_failed")
                    return@execute
                }

                val chunks = LocalTtsTextChunker.split(
                    text, TextToSpeech.getMaxSpeechInputLength(),
                )
                val allPcm = java.io.ByteArrayOutputStream()
                var chunkIndex = 0
                var waveFile: File? = null

                fun enqueueChunk() {
                    if (!gate.isCurrent(generation)) return
                    val candidate = File(appContext.cacheDir, "local-speech-${UUID.randomUUID()}.wav")
                    waveFile = candidate
                    synchronized(lock) { outputFile = candidate }
                    val result = tts.synthesizeToFile(
                        chunks[chunkIndex], Bundle.EMPTY, candidate,
                        "local-speech-$generation-$chunkIndex",
                    )
                    if (result != TextToSpeech.SUCCESS) {
                        finishWithError(generation, listener, "tts_queue_failed")
                    }
                }

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        appContext.mainExecutor.execute {
                            if (!gate.isCurrent(generation) ||
                                utteranceId != "local-speech-$generation-$chunkIndex"
                            ) return@execute
                            try {
                                val recordedFile = checkNotNull(waveFile)
                                val decoded = LocalSpeechPcm.decodeWaveToMonoPcm16(recordedFile.readBytes())
                                val target = LocalSpeechPcm.resampleLinear(
                                    decoded.monoSamples,
                                    decoded.sampleRate,
                                    LocalSpeechFormat.SAMPLE_RATE_HZ,
                                )
                                val raw = LocalSpeechPcm.toLittleEndianBytes(target)
                                if (raw.isEmpty()) {
                                    finishWithError(generation, listener, "tts_empty_pcm")
                                    return@execute
                                }
                                allPcm.write(raw)
                                recordedFile.delete()
                                chunkIndex += 1
                                if (chunkIndex < chunks.size) {
                                    enqueueChunk()
                                } else {
                                    val complete = allPcm.toByteArray()
                                    finishGeneration(generation)
                                    listener.onPcm16Mono16k(complete)
                                }
                            } catch (error: Throwable) {
                                finishWithError(generation, listener, "tts_decode_${error.javaClass.simpleName}")
                            }
                        }
                    }

                    @Deprecated("Deprecated by framework")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == "local-speech-$generation-$chunkIndex") {
                            finishWithError(generation, listener, "tts_synthesis_error")
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == "local-speech-$generation-$chunkIndex") {
                            finishWithError(generation, listener, "tts_synthesis_error_$errorCode")
                        }
                    }
                })
                enqueueChunk()
            }
        }
    }

    fun cancel() {
        gate.invalidate()
        cleanup()
    }

    override fun close() = cancel()

    private fun finishWithError(generation: Long, listener: Listener, reason: String) {
        if (!gate.isCurrent(generation)) return
        finishGeneration(generation)
        listener.onError(reason)
    }

    private fun finishGeneration(generation: Long) {
        if (!gate.isCurrent(generation)) return
        gate.invalidate()
        cleanup()
    }

    private fun cleanup() {
        val currentEngine: TextToSpeech?
        val currentFile: File?
        synchronized(lock) {
            currentEngine = engine
            currentFile = outputFile
            engine = null
            outputFile = null
        }
        try { currentEngine?.stop() } catch (_: Throwable) {}
        try { currentEngine?.shutdown() } catch (_: Throwable) {}
        try { currentFile?.delete() } catch (_: Throwable) {}
    }
}
