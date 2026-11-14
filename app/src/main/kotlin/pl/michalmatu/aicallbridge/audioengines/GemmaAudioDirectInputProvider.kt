package pl.michalmatu.aicallbridge.audioengines


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

/** One WAV allocation per inference turn, reused by LiteRT context/format retry.
 * The contents are only model input, never a persisted recording.
 * Close wipes the encoded copy; the caller owns and wipes the original PCM.
 */
internal class GemmaAudioWaveCache(private val pcm16: ByteArray) : AutoCloseable {
    private var wave: ByteArray? = null
    private var closed = false
    var encodeMs: Long = 0
        private set
    var reads: Int = 0
        private set

    fun bytes(): ByteArray {
        check(!closed) { "audio_wave_cache_closed" }
        wave?.let { reads++; return it }
        val started = System.nanoTime()
        val encoded = GemmaAudioPcm16.toWave(pcm16)
        encodeMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0)
        wave = encoded
        reads++
        return encoded
    }

    override fun close() {
        if (closed) return
        closed = true
        wave?.fill(0)
        wave = null
    }
}

/** Collects one utterance for the shared dialogue engine. Never allocates another model. */
internal class GemmaAudioDirectInputProvider : SpeechInputProvider {
    private val lock = Any()
    private var listener: SpeechInputProvider.Listener? = null
    // A bounded, reusable PCM buffer. Clearing only ByteArrayOutputStream's length
    // left old speech in its backing allocation after cancellation.
    private var pcm = ByteArray(0)
    private var used = 0
    private var closed = false
    // Incremented whenever the owner starts, cancels or closes an utterance.
    // Asynchronous delivery may only proceed for that same input generation.
    private var generation = 0L

    private fun clearPcm() {
        pcm.fill(0, 0, used)
        used = 0
    }

    override fun start(listener: SpeechInputProvider.Listener) {
        val token = synchronized(lock) {
            check(!closed) { "gemma_audio_input_closed" }
            clearPcm()
            this.listener = listener
            generation += 1
            generation
        }
        // Cancel/close/new start can supersede this turn before onReady runs.
        if (synchronized(lock) { !closed && generation == token && this.listener === listener }) {
            listener.onReady()
        }
    }

    override fun writePcm(bytes: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset <= bytes.size &&
            length <= bytes.size - offset) { "invalid_pcm_range" }
        return synchronized(lock) {
            if (closed || listener == null ||
                length > GemmaAudioPcm16.MAX_PCM_BYTES - used) return@synchronized false
            if (length == 0) return@synchronized true
            val required = used + length
            if (required > pcm.size) {
                var capacity = pcm.size.coerceAtLeast(32_000)
                while (capacity < required) {
                    capacity = (capacity * 2).coerceAtMost(GemmaAudioPcm16.MAX_PCM_BYTES)
                }
                val expanded = ByteArray(capacity)
                pcm.copyInto(expanded, endIndex = used)
                pcm.fill(0, 0, used)
                pcm = expanded
            }
            bytes.copyInto(pcm, destinationOffset = used, startIndex = offset,
                endIndex = offset + length)
            used += length
            true
        }
    }

    override fun finishInput() {
        val (callback, samples, token) = synchronized(lock) {
            val callback = listener ?: return
            listener = null
            val samples = pcm.copyOf(used)
            clearPcm()
            Triple(callback, samples, generation)
        }
        // Suppress a completed capture if its owner has already restarted or
        // cancelled it. Do not hold the PCM lock while calling the model.
        if (!synchronized(lock) { !closed && generation == token }) {
            samples.fill(0)
            return
        }
        if (samples.isEmpty() || samples.size % 2 != 0) {
            // Invalid speech must not remain in the temporary sample allocation.
            samples.fill(0)
            callback.onError("invalid_pcm16")
        } else {
            // The model owns PCM only after accepting the callback. Native submission
            // can throw synchronously when a runtime is closing; in that case the
            // capture layer still owns the buffer and must wipe it before reporting.
            try {
                callback.onFinalAudio(samples)
            } catch (_: IllegalArgumentException) {
                samples.fill(0)
                callback.onError("invalid_pcm16")
            } catch (_: IllegalStateException) {
                samples.fill(0)
                callback.onError("audio_model_unavailable")
            }
        }
    }

    override fun cancel() {
        synchronized(lock) {
            generation += 1
            listener = null
            clearPcm()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            listener = null
            clearPcm()
            pcm = ByteArray(0)
        }
    }
}
