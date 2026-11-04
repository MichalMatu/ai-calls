package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import android.os.SystemClock
import android.util.Log
import pl.michalmatu.aicallbridge.GateCHybridDialogueBackendFactory
import pl.michalmatu.aicallbridge.textagent.DialogueActionCatalog
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Prepares the SAME local Gemma action-classifier engine before Telecom places a
 * carrier call, instead of making the remote IVR wait for GPU/model cold-start.
 *
 * This owns no campaign authorization and cannot dial, disclose facts, speak,
 * commit an effect, grant permissions or affect another call.
 */
internal object CampaignGemmaPreflight {
    private val lock = Any()
    private var generation = 0L
    private var preparing = false
    private var pending: TextCallAgentBackend? = null
    private var prepared: TextCallAgentBackend? = null
    private var preparedProfile: AudioEngineProfile? = null
    private var inFlightProfile: AudioEngineProfile? = null
    private val callbacks = mutableListOf<(Boolean) -> Unit>()

    /** One model prewarm is shared by concurrent requests for the same app process. */
    fun prepare(
        context: Context,
        onReady: (Boolean) -> Unit,
        profile: AudioEngineProfile = AudioEngineProfile.STABLE,
    ) {
        val token: Long
        synchronized(lock) {
            if (prepared != null && preparedProfile == profile) {
                onReady(true)
                return
            }
            if (preparing && inFlightProfile != profile) {
                onReady(false)
                return
            }
            if (prepared != null) {
                prepared?.close()
                prepared = null
                preparedProfile = null
            }
            callbacks += onReady
            if (preparing) return
            preparing = true
            inFlightProfile = profile
            token = ++generation
        }

        val candidate = try {
            val policy = GateCHybridDialogueBackendFactory.actionPolicy(true)
            val instruction = DialogueActionCatalog.systemPrompt(
                policy,
                GateCHybridDialogueBackendFactory.actionContext(true),
            )
            DialogueEngineProviders.resolve(profile).createActionClassifier(
                context.applicationContext,
                instruction,
                policy.allowedActions,
            )
        } catch (error: Throwable) {
            Log.w(TAG, "campaign_gemma_prewarm_create_failed=${error.javaClass.simpleName}")
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
                "W jakiej sprawie Pan dzwoni?",
                object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        // The preflight is a synthetic benchmark, NOT the first
                        // message of the real consultant's persistent conversation.
                        // Keep the initialized Engine, discard this fake turn.
                        val clean = text.isNotBlank() && runCatching {
                            candidate.resetConversationContext()
                        }.isSuccess
                        complete(token, candidate, clean, SystemClock.elapsedRealtime() - startedAt)
                    }

                    override fun onError(reason: String) =
                        complete(token, candidate, false, SystemClock.elapsedRealtime() - startedAt)
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
                if (candidate != null) runCatching { candidate.close() }
                return
            }
            preparing = false
            pending = null
            val profile = inFlightProfile
            inFlightProfile = null
            if (ok && candidate != null) {
                prepared?.let { runCatching { it.close() } }
                prepared = candidate
                preparedProfile = profile
            } else {
                candidate?.let { runCatching { it.close() } }
            }
            notify = callbacks.toList()
            callbacks.clear()
        }
        Log.i(TAG, "campaign_gemma_prewarm_success=$ok,cold_start_ms=${durationMs.coerceAtLeast(0L)}")
        notify.forEach { callback -> runCatching { callback(ok) } }
    }

    /** Transfer model ownership to the application-authorized in-call action backend. */
    fun takePrepared(profile: AudioEngineProfile = AudioEngineProfile.STABLE): TextCallAgentBackend? =
        synchronized(lock) {
            val candidate = prepared
            val matches = preparedProfile == profile
            prepared = null
            preparedProfile = null
            if (!matches) {
                candidate?.close()
                null
            } else candidate
        }

    /** Cancels only an UNUSED model. The active call owns the model after takePrepared(). */
    fun discardUnused() {
        val leftovers: List<TextCallAgentBackend>
        val cancelled: List<(Boolean) -> Unit>
        synchronized(lock) {
            ++generation
            preparing = false
            leftovers = listOfNotNull(pending, prepared)
            pending = null
            prepared = null
            preparedProfile = null
            inFlightProfile = null
            cancelled = callbacks.toList()
            callbacks.clear()
        }
        leftovers.forEach { runCatching { it.close() } }
        cancelled.forEach { callback -> runCatching { callback(false) } }
    }

    private const val TAG = "AiCallBridge"
}
