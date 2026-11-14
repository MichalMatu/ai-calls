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
        // One exact-sized allocation instead of ByteArrayOutputStream's backing
        // array plus toByteArray(). Preserve every PCM sample without resampling.
        val wav = ByteArray(pcm.size + 44)
        fun ascii(offset: Int, value: String) {
            value.toByteArray(Charsets.US_ASCII).copyInto(wav, offset)
        }
        fun u16(offset: Int, value: Int) {
            wav[offset] = value.toByte()
            wav[offset + 1] = (value ushr 8).toByte()
        }
        fun u32(offset: Int, value: Int) {
            u16(offset, value and 65535)
            u16(offset + 2, value ushr 16)
        }
        ascii(0, "RIFF")
        u32(4, pcm.size + 36)
        ascii(8, "WAVEfmt ")
        u32(16, 16)
        u16(20, 1)
        u16(22, 1)
        u32(24, SAMPLE_RATE)
        u32(28, SAMPLE_RATE * 2)
        u16(32, 2)
        u16(34, 16)
        ascii(36, "data")
        u32(40, pcm.size)
        pcm.copyInto(wav, 44)
        return wav
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
