package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import android.os.SystemClock
import android.util.Log
import pl.michalmatu.aicallbridge.developerrelay.GemmaDirectTaskBrief
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.NativeConversationContextBackend
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/** Independent prewarm: never borrows the script/skill classifier or its history. */
internal object CampaignGemmaDirectPreflight {
    private val lock = Any()
    private var generation = 0L
    private var preparing = false
    private var pending: TextCallAgentBackend? = null
    private var prepared: TextCallAgentBackend? = null
    private var preparedInstruction: String? = null
    private var inFlightInstruction: String? = null
    private val callbacks = mutableListOf<(Boolean) -> Unit>()

    fun prepare(
        context: Context,
        systemInstruction: String = GemmaDirectTaskBrief.PROMPT,
        onReady: (Boolean) -> Unit,
    ) {
        require(systemInstruction.isNotBlank())
        val token: Long
        synchronized(lock) {
            if (prepared != null && preparedInstruction == systemInstruction) {
                onReady(true)
                return
            }
            if (preparing && inFlightInstruction != systemInstruction) {
                // A different task/skills choice must not reuse an old engine.
                onReady(false)
                return
            }
            if (prepared != null) {
                prepared?.close()
                prepared = null
                preparedInstruction = null
            }
            callbacks += onReady
            if (preparing) return
            preparing = true
            inFlightInstruction = systemInstruction
            token = ++generation
        }
        val candidate = runCatching {
            Gemma4LiteRtTextBackendFactory.createDirectDialogue(
                context.applicationContext, systemInstruction,
            )
        }.getOrElse {
            Log.w("AiCallBridge", "direct_prewarm_create_failed=${it.javaClass.simpleName}")
            complete(token, null, false, 0L)
            return
        }
        synchronized(lock) {
            if (!preparing || generation != token) {
                candidate.close()
                return
            }
            pending = candidate
        }
        val startedAt = SystemClock.elapsedRealtime()
        try {
            candidate.generate(
                "Dzień dobry, w jakiej sprawie Pan dzwoni?",
                object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        val clean = text.isNotBlank() && runCatching {
                            (candidate as NativeConversationContextBackend).resetConversationContext()
                        }.isSuccess
                        complete(token, candidate, clean, SystemClock.elapsedRealtime() - startedAt)
                    }

                    override fun onError(reason: String) {
                        complete(token, candidate, false, SystemClock.elapsedRealtime() - startedAt)
                    }
                },
            )
        } catch (error: Throwable) {
            complete(token, candidate, false, SystemClock.elapsedRealtime() - startedAt)
        }
    }

    private fun complete(token: Long, candidate: TextCallAgentBackend?, ok: Boolean, durationMs: Long) {
        val notify: List<(Boolean) -> Unit>
        synchronized(lock) {
            if (!preparing || generation != token) {
                candidate?.close()
                return
            }
            preparing = false
            pending = null
            inFlightInstruction = null
            if (ok && candidate != null) {
                prepared?.close()
                prepared = candidate
                preparedInstruction = inFlightInstruction
            } else {
                candidate?.close()
            }
            notify = callbacks.toList()
            callbacks.clear()
        }
        Log.i("AiCallBridge", "direct_prewarm_success=$ok,cold_start_ms=${durationMs.coerceAtLeast(0)}")
        notify.forEach { callback -> runCatching { callback(ok) } }
    }

    fun takePrepared(
        systemInstruction: String = GemmaDirectTaskBrief.PROMPT,
    ): TextCallAgentBackend? = synchronized(lock) {
        val candidate = prepared
        val matching = preparedInstruction == systemInstruction
        prepared = null
        preparedInstruction = null
        if (!matching) {
            candidate?.close()
            null
        } else candidate
    }

    fun discardUnused() {
        val leftovers: List<TextCallAgentBackend>
        val cancelled: List<(Boolean) -> Unit>
        synchronized(lock) {
            ++generation
            preparing = false
            leftovers = listOfNotNull(pending, prepared)
            pending = null
            prepared = null
            inFlightInstruction = null
            preparedInstruction = null
            cancelled = callbacks.toList()
            callbacks.clear()
        }
        leftovers.forEach { runCatching { it.close() } }
        cancelled.forEach { callback -> runCatching { callback(false) } }
    }
}
