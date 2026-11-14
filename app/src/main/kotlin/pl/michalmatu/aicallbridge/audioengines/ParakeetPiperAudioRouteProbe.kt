package pl.michalmatu.aicallbridge.audioengines

/** Fixed synthetic Polish speech only: no microphone, Telecom, numbers or transcripts in receipts. */
internal object ParakeetPiperAudioRoutePolicy {
    const val PHRASE = "Dzień dobry. Proszę o pomoc konsultanta."

    fun canProbe(profile: AudioEngineProfile): Boolean =
        profile.input == SpeechInputId.PARAKEET_PL &&
            profile.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE &&
            profile.voiceId != null && profile.languageTag == "pl-PL"
}

internal data class ParakeetPiperAudioRouteResult(
    val passed: Boolean,
    val code: String,
    val ttsMs: Long = 0,
    val nativePrewarmMs: Long = 0,
    val nativeInferMs: Long = 0,
    val wordErrors: Int = -1,
    val referenceWords: Int = -1,
)

/** One in-memory TTS -> PCM16 -> native STT sample. No real phone activity or persisted transcript.
 * Factories/dispatcher make cancellation and scoring testable without Android device access.
 */
internal class ParakeetPiperAudioRouteRunner(
    private val outputFactory: () -> SpeechOutputProvider,
    private val inputFactory: () -> SpeechInputProvider,
    private val dispatch: (() -> Unit) -> Unit,
    private val clock: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val lock = Any()
    private var active = true
    private var started = false
    private var inputReady = false
    private var output: SpeechOutputProvider? = null
    private var input: SpeechInputProvider? = null
    private var pcm: ByteArray? = null
    private var callback: ((ParakeetPiperAudioRouteResult) -> Unit)? = null
    private var startedTtsAt = 0L
    private var startedNativeAt = 0L
    private var startedInferAt = 0L
    private var ttsMs = 0L
    private var nativePrewarmMs = 0L

    fun start(done: (ParakeetPiperAudioRouteResult) -> Unit) {
        synchronized(lock) {
            check(active && !started) { "audio_route_probe_not_startable" }
            started = true
            callback = done
            startedTtsAt = clock()
        }
        val producer = try { outputFactory() }
        catch (_: Exception) { finish("tts_unavailable"); return }
        synchronized(lock) { if (active) output = producer else { producer.close(); return } }
        try {
            producer.synthesize(ParakeetPiperAudioRoutePolicy.PHRASE,
                object : SpeechOutputProvider.Listener {
                    override fun onPcm16Mono16k(bytes: ByteArray) {
                        dispatch {
                            if (!isActive()) { bytes.fill(0); return@dispatch }
                            ttsMs = elapsed(startedTtsAt)
                            if (bytes.size < 8_000 || bytes.size % 2 != 0) {
                                bytes.fill(0)
                                finish("invalid_pcm")
                            } else {
                                synchronized(lock) { pcm = bytes }
                                beginInput()
                            }
                        }
                    }
                    override fun onError(reason: String) {
                        dispatch { finish("tts_synthesis_failed") }
                    }
                })
        } catch (_: Exception) { finish("tts_synthesis_failed") }
    }

    private fun beginInput() {
        if (!isActive()) return
        val receiver = try { inputFactory() }
        catch (_: Exception) { finish("parakeet_unavailable"); return }
        synchronized(lock) { if (active) input = receiver else { receiver.close(); return } }
        startedNativeAt = clock()
        try {
            receiver.start(object : SpeechInputProvider.Listener {
                override fun onReady() {
                    dispatch {
                        if (!isActive()) return@dispatch
                        val firstReady = synchronized(lock) {
                            if (inputReady) false else { inputReady = true; true }
                        }
                        if (!firstReady) return@dispatch
                        nativePrewarmMs = elapsed(startedNativeAt)
                        val current = synchronized(lock) { pcm }
                        if (current == null) { finish("pcm_unavailable"); return@dispatch }
                        startedInferAt = clock()
                        try {
                            for (offset in current.indices step 32_000) {
                                val size = minOf(32_000, current.size - offset)
                                if (!receiver.writePcm(current, offset, size)) {
                                    finish("pcm_backpressure")
                                    return@dispatch
                                }
                            }
                            synchronized(lock) { if (pcm === current) pcm = null }
                            current.fill(0)
                            receiver.finishInput()
                        } catch (_: Exception) { finish("pcm_input_failed") }
                    }
                }
                override fun onFinalTranscript(text: String) {
                    dispatch {
                        if (!isActive()) return@dispatch
                        val (errors, words) = GemmaAudioCorpus.wordErrors(
                            ParakeetPiperAudioRoutePolicy.PHRASE, text)
                        val passed = text.isNotBlank() && words > 0 && errors * 2 <= words
                        finish(if (passed) "ok" else "transcript_mismatch", errors, words)
                    }
                }
                override fun onError(reason: String) {
                    dispatch { finish("parakeet_native_failed") }
                }
            })
        } catch (_: Exception) { finish("parakeet_native_failed") }
    }

    private fun isActive() = synchronized(lock) { active }
    private fun elapsed(start: Long): Long =
        ((clock() - start) / 1_000_000L).coerceAtLeast(0L)

    private fun finish(code: String, errors: Int = -1, words: Int = -1) {
        val result = ParakeetPiperAudioRouteResult(
            passed = code == "ok", code = code, ttsMs = ttsMs,
            nativePrewarmMs = nativePrewarmMs,
            nativeInferMs = if (startedInferAt == 0L) 0 else elapsed(startedInferAt),
            wordErrors = errors, referenceWords = words)
        val done = synchronized(lock) {
            if (!active) return
            val cb = callback
            callback = null
            cb
        }
        close()
        dispatch { done?.invoke(result) }
    }

    override fun close() {
        val resources = synchronized(lock) {
            if (!active) return
            active = false
            callback = null
            val items = Triple(output, input, pcm)
            output = null; input = null; pcm = null
            items
        }
        resources.third?.fill(0)
        runCatching { resources.first?.close() }
        runCatching { resources.second?.close() }
    }
}
