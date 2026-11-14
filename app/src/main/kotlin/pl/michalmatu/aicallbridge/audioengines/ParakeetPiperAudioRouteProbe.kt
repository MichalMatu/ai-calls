package pl.michalmatu.aicallbridge.audioengines

import java.security.MessageDigest

/** Fixed synthetic Polish speech only: no microphone, Telecom, numbers or transcripts in receipts. */
internal object ParakeetPiperAudioRoutePolicy {
    const val PHRASE = "Dzień dobry. Proszę o pomoc konsultanta."

    fun canProbe(profile: AudioEngineProfile): Boolean =
        profile.input == SpeechInputId.PARAKEET_PL &&
            profile.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE &&
            profile.voiceId != null && profile.languageTag == "pl-PL"

    /** Return only an allowlisted technical TTS code, never raw provider text. */
    fun safeTtsError(reason: String): String = when {
        reason in setOf(
            "tts_init_failed", "explicit_tts_engine_unavailable",
            "local_voice_missing", "tts_set_voice_failed", "tts_queue_failed",
            "tts_empty_pcm", "tts_synthesis_error"
        ) -> reason
        reason.matches(Regex("tts_synthesis_error_-?[0-9]{1,4}")) -> reason
        reason.startsWith("tts_decode_") -> "tts_decode_failed"
        else -> "tts_synthesis_failed"
    }
}

/** Frozen, public, artificial Polish phrases; version and digest tie every run to exact inputs. */
internal data class FixedAudioCase(val id: String, val phrase: String)

internal object ParakeetPiperFixedCorpus {
    const val ID = "pl-audio-v1"
    const val REPEATS = 2
    val cases = listOf(
        FixedAudioCase("greeting", ParakeetPiperAudioRoutePolicy.PHRASE),
        FixedAudioCase("appointment",
            "Chciałbym umówić wizytę we wtorek o godzinie piętnastej trzydzieści."),
        FixedAudioCase("correction",
            "Nie w środę, tylko w czwartek. Proszę poprawić termin."),
        FixedAudioCase("long",
            "Mam pytanie dotyczące wizyty, którą próbowałem umówić w ubiegłym tygodniu. " +
                "Chciałbym sprawdzić, czy termin nadal jest aktualny, a jeśli nie, wybrać inny dzień."),
        FixedAudioCase("goodbye", "Dziękuję za pomoc. Do widzenia."),
    )
    fun find(id: String): FixedAudioCase? = cases.firstOrNull { it.id == id }
    val sha256: String by lazy {
        val manifest = cases.joinToString(separator = "") { it.id + "=" + it.phrase + "\n" }
        MessageDigest.getInstance("SHA-256").digest(manifest.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
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
    private val phrase: String = ParakeetPiperAudioRoutePolicy.PHRASE,
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
            producer.synthesize(phrase,
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
                        val safeCode = ParakeetPiperAudioRoutePolicy.safeTtsError(reason)
                        dispatch { finish(safeCode) }
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
                            phrase, text)
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
