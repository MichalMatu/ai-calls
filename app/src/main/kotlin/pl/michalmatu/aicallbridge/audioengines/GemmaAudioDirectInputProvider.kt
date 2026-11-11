package pl.michalmatu.aicallbridge.audioengines

import java.io.ByteArrayOutputStream

/** An isolated 16-kHz mono signed PCM16 utterance, never a recording file. */
internal object GemmaAudioPcm16 {
    const val SAMPLE_RATE = 16_000
    const val MAX_SECONDS = 30
    const val MAX_PCM_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS

    fun validate(pcm: ByteArray) {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0) { "invalid_pcm16" }
        require(pcm.size <= MAX_PCM_BYTES) { "audio_exceeds_model_window" }
    }

    fun toWave(pcm: ByteArray): ByteArray {
        validate(pcm)
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun u16(value: Int) {
            out.write(value and 255)
            out.write((value ushr 8) and 255)
        }
        fun u32(value: Int) {
            u16(value and 65535)
            u16((value ushr 16) and 65535)
        }
        ascii("RIFF")
        u32(pcm.size + 36)
        ascii("WAVEfmt ")
        u32(16)
        u16(1)
        u16(1)
        u32(SAMPLE_RATE)
        u32(SAMPLE_RATE * 2)
        u16(2)
        u16(16)
        ascii("data")
        u32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}

/** Collects one utterance for the shared dialogue engine. Never allocates another model. */
internal class GemmaAudioDirectInputProvider : SpeechInputProvider {
    private val lock = Any()
    private var listener: SpeechInputProvider.Listener? = null
    private val pcm = ByteArrayOutputStream()

    override fun start(listener: SpeechInputProvider.Listener) {
        synchronized(lock) { pcm.reset(); this.listener = listener }
        listener.onReady()
    }

    override fun writePcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset <= bytes.size &&
            length <= bytes.size - offset) { "invalid_pcm_range" }
        return synchronized(lock) {
            if (listener == null || length > GemmaAudioPcm16.MAX_PCM_BYTES - pcm.size()) false
            else { pcm.write(bytes, offset, length); true }
        }
    }

    override fun finishInput() {
        val (callback, samples) = synchronized(lock) {
            val callback = listener ?: return
            listener = null
            callback to pcm.toByteArray().also { pcm.reset() }
        }
        if (samples.isEmpty() || samples.size % 2 != 0) callback.onError("invalid_pcm16")
        else callback.onFinalAudio(samples)
    }

    override fun cancel() { synchronized(lock) { listener = null; pcm.reset() } }
    override fun close() = cancel()
}
