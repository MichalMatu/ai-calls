package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import com.google.gson.JsonParser
import java.util.Locale
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.NativeConversationContextBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Synthetic, off-call Polish audio corpus, never Telecom or real recordings. */
internal object GemmaAudioCorpus {
    val phrases = listOf(
        "Dzień dobry. Proszę o pomoc konsultanta.",
        "Chcę sprawdzić ustawienia prezentacji numeru telefonu.",
        "Proszę powtórzyć ostatnią informację.",
    )
    private fun words(s: String) = Regex("[\\p{L}\\p{N}]+")
        .findAll(s.lowercase(Locale.forLanguageTag("pl-PL"))).map { it.value }.toList()

    fun decodeTranscriptOnly(raw: String): String? = runCatching {
        val objectValue = JsonParser.parseString(raw).asJsonObject
        if (objectValue.keySet() != setOf("transcript")) return@runCatching null
        val field = objectValue.get("transcript")
        if (field == null || !field.isJsonPrimitive || !field.asJsonPrimitive.isString) null
        else field.asString.trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Fixed diagnostic vocabulary only; raw speech/reply cannot leave the app. */
    fun invalidReplyCode(raw: String): String {
        val result = runCatching { ConversationReplyProtocol.decodeAudio(raw) }
        val failure = result.exceptionOrNull()?.message.orEmpty()
        return when (failure) {
            "audio_transcript_missing", "audio_transcript_empty" -> "transcript_missing"
            "conversation_reply_fields", "conversation_reply_type_reply",
            "conversation_reply_type_tool" -> "reply_fields"
            else -> "reply_format_invalid"
        }
    }

    fun wordErrors(reference: String, actual: String): Pair<Int, Int> {
        val a = words(reference)
        val b = words(actual)
        var previous = IntArray(b.size + 1) { it }
        a.forEachIndexed { i, expected ->
            val next = IntArray(b.size + 1)
            next[0] = i + 1
            b.forEachIndexed { j, observed ->
                next[j + 1] = minOf(
                    next[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (expected == observed) 0 else 1,
                )
            }
            previous = next
        }
        return previous.last() to a.size
    }
}

internal data class GemmaAudioCorpusCase(
    val number: Int,
    val passed: Boolean,
    val code: String,
    val ttsMs: Long,
    val nativeMs: Long,
    val wordErrors: Int,
    val referenceWords: Int,
)

/** The same native model is retained across three synthetic sentences.
 * Callbacks are serialised on the supplied app-main dispatcher; no PCM or
 * transcription is persisted, returned by USB or placed in task receipts.
 */
internal class GemmaAudioCorpusRunner(
    private val model: TextCallAgentBackend,
    private val ttsFactory: () -> SpeechOutputProvider,
    private val dispatch: (() -> Unit) -> Unit,
    private val transcriptOnly: Boolean = false,
    private val clock: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var started = false
    private var caseNumber = 0
    private var epoch = 0L
    private var done: ((List<GemmaAudioCorpusCase>) -> Unit)? = null
    private var output: SpeechOutputProvider? = null
    private val cases = mutableListOf<GemmaAudioCorpusCase>()

    fun start(complete: (List<GemmaAudioCorpusCase>) -> Unit) {
        synchronized(lock) {
            check(!closed && !started) { "audio_corpus_not_startable" }
            started = true
            done = complete
        }
        try { model.prewarm() } catch (_: Exception) { finishAll("native_start_failed"); return }
        next()
    }

    private fun next() {
        val number = synchronized(lock) {
            if (closed) return
            if (caseNumber >= GemmaAudioCorpus.phrases.size) {
                val callback = done
                done = null
                val results = cases.toList()
                close()
                callback?.invoke(results)
                return
            }
            epoch += 1
            caseNumber + 1
        }
        val token = synchronized(lock) { epoch }
        val startedAt = clock()
        val tts = try { ttsFactory() } catch (_: Exception) {
            finishCase(token, "tts_unavailable", startedAt, 0, null)
            return
        }
        synchronized(lock) { if (!closed && epoch == token) output = tts else tts.close() }
        if (!current(token)) return
        try {
            tts.synthesize(GemmaAudioCorpus.phrases[number - 1],
                object : SpeechOutputProvider.Listener {
                    override fun onPcm16Mono16k(pcm: ByteArray) {
                        dispatch {
                            if (!current(token)) { pcm.fill(0); return@dispatch }
                            val ttsMs = elapsed(startedAt)
                            runCatching { tts.close() }
                            synchronized(lock) { if (output === tts) output = null }
                            val failure = ModelAudioProbePcm.failure(pcm)
                            if (failure != null) {
                                pcm.fill(0)
                                finishCase(token, "invalid_pcm", startedAt, ttsMs, null)
                                return@dispatch
                            }
                            val nativeStarted = clock()
                            try {
                                model.generateAudio(pcm, object : TextCallAgentBackend.Listener {
                                    override fun onComplete(text: String) {
                                        dispatch {
                                            if (!current(token)) return@dispatch
                                            val outcome = runCatching {
                                                val transcript = if (transcriptOnly)
                                                    GemmaAudioCorpus.decodeTranscriptOnly(text)
                                                else ConversationReplyProtocol.decodeAudio(text).first
                                                checkNotNull(transcript) { "transcript_missing" }
                                                val score = GemmaAudioCorpus.wordErrors(
                                                    GemmaAudioCorpus.phrases[number - 1], transcript)
                                                Triple(score.first, score.second, true)
                                            }.getOrNull()
                                            val code = when {
                                                outcome == null -> GemmaAudioCorpus.invalidReplyCode(text)
                                                !outcome.third -> "unexpected_tool"
                                                outcome.first * 2 > outcome.second -> "transcript_mismatch"
                                                else -> "ok"
                                            }
                                            finishCase(token, code, nativeStarted, ttsMs, outcome)
                                        }
                                    }
                                    override fun onError(reason: String) {
                                        dispatch {
                                            if (current(token)) finishCase(
                                                token,
                                                if (reason == "conversation_reply_invalid" ||
                                                    reason == "gemma4_runtime_conversation_reply_invalid")
                                                    "reply_rejected" else "native_error",
                                                nativeStarted, ttsMs, null)
                                        }
                                    }
                                })
                            } catch (_: Exception) {
                                finishCase(token, "native_start_failed", nativeStarted, ttsMs, null)
                            } finally {
                                // The LiteRT runtime copies the model input when enqueued.
                                pcm.fill(0)
                            }
                        }
                    }
                    override fun onError(reason: String) {
                        dispatch {
                            if (current(token)) finishCase(
                                token, "tts_unavailable", startedAt, elapsed(startedAt), null)
                        }
                    }
                })
        } catch (_: Exception) {
            finishCase(token, "tts_unavailable", startedAt, elapsed(startedAt), null)
        }
    }

    private fun finishCase(
        token: Long, code: String, nativeStart: Long, ttsMs: Long,
        outcome: Triple<Int, Int, Boolean>?,
    ) {
        val accepted = synchronized(lock) {
            if (closed || token != epoch || caseNumber >= GemmaAudioCorpus.phrases.size) false
            else {
                epoch += 1
                caseNumber += 1
                cases.add(GemmaAudioCorpusCase(
                    caseNumber, code == "ok", code, ttsMs.coerceAtLeast(0L),
                    if (ttsMs == 0L) 0L else elapsed(nativeStart),
                    outcome?.first ?: -1, outcome?.second ?: -1,
                ))
                true
            }
        }
        if (!accepted) return
        runCatching { output?.close() }
        output = null
        runCatching { (model as? NativeConversationContextBackend)?.resetConversationContext() }
        next()
    }

    private fun finishAll(code: String) {
        val callback = synchronized(lock) {
            if (closed) return
            val failed = (caseNumber + 1..GemmaAudioCorpus.phrases.size).map {
                GemmaAudioCorpusCase(it, false, code, 0, 0, -1, -1)
            }
            cases.addAll(failed)
            val result = done
            done = null
            result
        }
        val result = cases.toList()
        close()
        callback?.invoke(result)
    }

    private fun elapsed(started: Long) =
        ((clock() - started) / 1_000_000L).coerceAtLeast(0L)

    private fun current(token: Long) = synchronized(lock) { !closed && token == epoch }

    override fun close() {
        val tts: SpeechOutputProvider?
        synchronized(lock) {
            if (closed) return
            closed = true
            epoch += 1
            done = null
            tts = output
            output = null
        }
        runCatching { tts?.close() }
        runCatching { model.close() }
    }
}

/** Strictly diagnostic entrypoint: does not save an audio profile or start a Call. */
internal object GemmaAudioCorpusProbe {
    fun create(context: Context, profile: AudioEngineProfile,
        transcriptOnly: Boolean = false): GemmaAudioCorpusRunner {
        val file = Gemma4LiteRtTextBackendFactory.modelFile(context, profile)
        val backend = if (transcriptOnly) Gemma4LiteRtTextBackendFactory.createTranscriptionProbe(file)
            else Gemma4LiteRtTextBackendFactory.createCompatibilityProbe(file, audio = true)
        return GemmaAudioCorpusRunner(
            model = backend,
            ttsFactory = {
                AndroidTtsOutputProvider(
                    context, profile.languageTag, profile.voiceId, profile.effectiveTtsEnginePackage)
            },
            dispatch = { action -> context.mainExecutor.execute(action) },
            transcriptOnly = transcriptOnly,
        )
    }
}
