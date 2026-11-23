package pl.michalmatu.aicallbridge.data.adapters

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.concurrent.Executors

/**
 * Native engine/session lifetime for the model/voice settings screen.
 * The view owns its generation fence and visible state; this adapter owns the
 * Android engine, background inventory worker, preview and shutdown.
 */
internal class AndroidTtsVoiceSession(private val context: Context) : AutoCloseable {
    private var engine: TextToSpeech? = null
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "model-voice-inventory").apply { isDaemon = true }
    }

    fun hasEngine(): Boolean = engine != null

    fun stop() {
        engine?.stop()
    }

    fun availableVoiceNames(): List<String> = engine?.voices.orEmpty().map { it.name }

    fun load(
        requestedEngine: String?,
        language: String,
        accepts: () -> Boolean,
        onFailure: () -> Unit,
        onReady: (AndroidTtsVoiceInventoryReader.Snapshot?) -> Unit,
    ) {
        engine?.shutdown()
        engine = null
        val holder = arrayOfNulls<TextToSpeech>(1)
        val initialized = TextToSpeech.OnInitListener { result ->
            context.mainExecutor.execute {
                val candidate = holder[0] ?: return@execute
                if (!accepts()) {
                    candidate.shutdown()
                    return@execute
                }
                if (result != TextToSpeech.SUCCESS) {
                    candidate.shutdown()
                    if (engine === candidate) engine = null
                    onFailure()
                    return@execute
                }
                worker.execute {
                    val inventory = runCatching {
                        AndroidTtsVoiceInventoryReader.read(candidate, requestedEngine, language)
                    }.getOrNull()
                    context.mainExecutor.execute ready@{
                        if (!accepts()) return@ready
                        onReady(inventory)
                    }
                }
            }
        }
        holder[0] = if (requestedEngine == null) TextToSpeech(context, initialized)
            else TextToSpeech(context, initialized, requestedEngine)
        engine = holder[0]
    }

    fun preview(selected: Voice, text: String, deferVoiceLoad: Boolean): Boolean {
        val current = engine ?: return false
        if (!deferVoiceLoad && current.setVoice(selected) != TextToSpeech.SUCCESS) return false
        current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "model-voice-preview")
        return true
    }

    override fun close() {
        engine?.shutdown()
        engine = null
        worker.shutdownNow()
    }
}
