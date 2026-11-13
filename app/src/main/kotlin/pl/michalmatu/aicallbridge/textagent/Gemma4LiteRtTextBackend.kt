package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol
import pl.michalmatu.aicallbridge.conversation.ConversationReplyRecovery
import pl.michalmatu.aicallbridge.conversation.InvalidConversationReply
import pl.michalmatu.aicallbridge.conversation.sendWithReplyRecovery
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

// Native engine allocation, not an artificial limit on user dialogue turns.
private const val DEFAULT_CONTEXT_TOKENS = 2_048

/** G1 experimental model isolation: keep existing engines unchanged for accepted models. */
internal data class NativeModelExecutionPolicy(
    val cpuOnly: Boolean = false,
    val newEngineAfterContextReset: Boolean = false,
)

internal fun DialogueModelId.nativeExecutionPolicy(): NativeModelExecutionPolicy = when (this) {
    DialogueModelId.QWEN_3_5_2B -> NativeModelExecutionPolicy(
        cpuOnly = true, newEngineAfterContextReset = true)
    else -> NativeModelExecutionPolicy()
}

/** One off-call measurement; counts characters, never guesses the model tokenizer's TPS.
 * No prompt, reply, model path, user data or telephone state can enter this record. */
internal data class NativeModelTiming(
    val backend: String,
    val contextTokens: Int,
    val engineReadyMs: Long,
    val conversationCreateMs: Long,
    val generateMs: Long,
    val conversationReused: Boolean,
    val turn: Int,
    val outputChars: Int,
    val status: String,
    val nativeCancelRequested: Boolean = false,
    val contextRecoveries: Int = 0,
    val contextRecoveryAttempts: Int = 0,
    val nativeContextTokens: Int? = null,
    val retainedContextTurns: Int = 0,
    val replyRecoveryAttempts: Int = 0,
    val replyValidationFailure: String = "none",
) {
    val totalMs: Long get() = engineReadyMs + conversationCreateMs + generateMs
}

internal fun interface Gemma4RuntimeFactory {
    fun create(modelPath: String, systemInstruction: String?): Gemma4Runtime
}

internal interface Gemma4Runtime : AutoCloseable {
    fun generate(userText: String, listener: TextCallAgentBackend.Listener)
    fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
        listener.onError("model_audio_input_unavailable")
    }
    fun cancel()
    /** Drop only synthetic prewarm history, not the initialized model engine. */
    fun resetConversationContext() = Unit
    /** Only between turns. Refresh creates a fresh Conversation, not a new Engine. */
    fun replaceSystemInstruction(instruction: String) {
        throw UnsupportedOperationException("runtime_system_instruction_refresh_not_supported")
    }
    override fun close()
}

/** Model-native chat context persists per owned call instead of prompt-duplicating recent turns. */
internal interface NativeConversationContextBackend {
    fun resetConversationContext()
}

/** Lab-only opt-in; never add new Skill text to untrusted STT/user utterances. */
internal interface NativeConversationInstructionBackend {
    fun refreshConversationInstruction(instruction: String)
}

/**
 * Generation-safe TextCall backend for direct Gemma 4 LiteRT-LM inference.
 *
 * The runtime remains an inference provider only. Stale generations are discarded here and every
 * accepted model response still flows through the caller's normal dialogue-skill/output policy.
 */
internal class Gemma4LiteRtTextBackend(
    private val runtimeFactory: Gemma4RuntimeFactory,
    private val modelPath: String,
    systemInstruction: String? = null,
) : TextCallAgentBackend, NativeConversationContextBackend,
    NativeConversationInstructionBackend {
    private val lock = Any()
    private var activeSystemInstruction = systemInstruction
    private var generation = 0L
    private var runtime: Gemma4Runtime? = null
    private var closed = false

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        request(listener) { runtime, guarded -> runtime.generate(userText, guarded) }
    }

    override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
        require(pcm16.isNotEmpty() && pcm16.size % 2 == 0) { "invalid_pcm16" }
        request(listener) { runtime, guarded -> runtime.generateAudio(pcm16, guarded) }
    }

    private fun request(listener: TextCallAgentBackend.Listener,
        send: (Gemma4Runtime, TextCallAgentBackend.Listener) -> Unit) {
        val activeRuntime: Gemma4Runtime
        val requestGeneration: Long
        synchronized(lock) {
            check(!closed) { "gemma4_backend_closed" }
            generation += 1
            requestGeneration = generation
            val existing = runtime
            if (existing == null) {
                activeRuntime = runtimeFactory.create(modelPath, activeSystemInstruction)
                runtime = activeRuntime
            } else {
                existing.cancel()
                activeRuntime = existing
            }
        }

        try {
            send(activeRuntime, guardedListener(requestGeneration, listener))
        } catch (error: Throwable) {
            if (isCurrent(requestGeneration)) {
                listener.onError("gemma4_runtime_${sanitize(error.javaClass.simpleName)}")
            }
        }
    }

    /** Only the off-call Lab dynamic wrapper opts into this operation. */
    override fun refreshConversationInstruction(instruction: String) {
        require(instruction.isNotBlank()) { "native_instruction_blank" }
        synchronized(lock) {
            check(!closed) { "gemma4_backend_closed" }
            runtime?.replaceSystemInstruction(instruction)
            activeSystemInstruction = instruction
        }
    }

    /** Reset preflight's synthetic utterance without shutting down the prewarmed engine. */
    override fun resetConversationContext() {
        val activeRuntime = synchronized(lock) { if (closed) null else runtime }
        activeRuntime?.resetConversationContext()
    }

    override fun cancel() {
        val activeRuntime = synchronized(lock) {
            if (closed) return
            generation += 1
            runtime
        }
        try {
            activeRuntime?.cancel()
        } catch (_: Throwable) {
            // Cancellation is best-effort after local generation has already been invalidated.
        }
    }

    override fun close() {
        val activeRuntime = synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            val current = runtime
            runtime = null
            current
        }
        try {
            activeRuntime?.close()
        } catch (_: Throwable) {
            // Closing remains idempotent even if native teardown already happened.
        }
    }

    private fun guardedListener(
        requestGeneration: Long,
        listener: TextCallAgentBackend.Listener,
    ) = object : TextCallAgentBackend.Listener {
        override fun onUserTranscript(text: String) {
            if (isCurrent(requestGeneration)) listener.onUserTranscript(text)
        }
        override fun onComplete(text: String) {
            if (isCurrent(requestGeneration)) listener.onComplete(text)
        }

        override fun onError(reason: String) {
            if (isCurrent(requestGeneration)) {
                listener.onError("gemma4_runtime_${sanitize(reason)}")
            }
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        !closed && generation == requestGeneration
    }

    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').take(MAX_ERROR_CHARS)

    private companion object {
        const val MAX_ERROR_CHARS = 160
    }
}

/** Direct in-process LiteRT-LM owner used by [Gemma4LiteRtTextBackend]. */
internal class LiteRtGemma4Runtime(
    private val modelPath: String,
    systemInstruction: String?,
    private val responseFormat: ResponseFormat? = null,
    private val audioResponseFormat: ResponseFormat? = null,
    private val disableQwenThinking: Boolean = false,
    private val executionPolicy: NativeModelExecutionPolicy = NativeModelExecutionPolicy(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aicall-gemma4-litert").apply { isDaemon = true }
    },
    private val contextTokenBudget: Int = DEFAULT_CONTEXT_TOKENS,
    // Non-authoritative diagnostic signal immediately before native sendMessage.
    private val onNativeSendEntered: () -> Unit = {},
    private val onTiming: (NativeModelTiming) -> Unit = {},
    private val validateConversationReply: Boolean = false,
) : Gemma4Runtime {
    init { require(contextTokenBudget > 0) { "invalid_native_context_budget" } }

    private val lock = Any()
    private var systemInstruction: String? = systemInstruction
    private var generation = 0L
    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    /** A normal between-turn cancel invalidates a callback but must not cancel native chat. */
    private var sendingGeneration: Long? = null
    private var nativeCancelRequestedGeneration: Long? = null
    private var discardAfterSend = false
    private var conversationTurnCount = 0
    private val conversationWindow = NativeConversationWindow()
    @Volatile private var activeBackendLabel = "unknown"
    private var closed = false

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        enqueue(userText, null, listener)
    }

    override fun generateAudio(pcm16: ByteArray, listener: TextCallAgentBackend.Listener) {
        if (audioResponseFormat == null) {
            listener.onError("model_audio_input_unavailable")
            return
        }
        pl.michalmatu.aicallbridge.audioengines.GemmaAudioPcm16.validate(pcm16)
        enqueue(ConversationReplyProtocol.AUDIO_INSTRUCTION, pcm16.copyOf(), listener)
    }

    private fun enqueue(userText: String, pcm16: ByteArray?, listener: TextCallAgentBackend.Listener) {
        val requestGeneration = synchronized(lock) {
            check(!closed) { "runtime_closed" }
            generation += 1
            generation
        }
        try {
            executor.execute {
                runGeneration(userText, requestGeneration, listener, pcm16)
            }
        } catch (_: RejectedExecutionException) {
            pcm16?.fill(0)
            if (isCurrent(requestGeneration)) listener.onError("executor_rejected")
        }
    }

    override fun cancel() {
        val currentlySending = synchronized(lock) {
            if (closed) return
            generation += 1
            // TextCallTurnController calls cancel after every completed output.
            // Calling native cancelProcess on an IDLE conversation destroys its context.
            if (sendingGeneration != null) {
                nativeCancelRequestedGeneration = sendingGeneration
                discardAfterSend = true
                activeConversation
            } else null
        }
        try {
            currentlySending?.cancelProcess()
        } catch (_: Throwable) {
            // The generation gate and discard flag still invalidate the work.
        }
    }

    override fun replaceSystemInstruction(instruction: String) {
        require(instruction.isNotBlank()) { "native_instruction_blank" }
        synchronized(lock) {
            check(!closed) { "runtime_closed" }
            check(sendingGeneration == null) { "native_instruction_refresh_while_generating" }
            systemInstruction = instruction
        }
        // Recreate Conversation with the new instruction while reusing the Engine.
        // Old history is supplied as quoted, data-only context by the Lab adapter.
        resetConversationContext()
    }

    override fun resetConversationContext() {
        // Synthetic reset or Lab instruction refresh. Keep the initialized Engine alive.
        val detached = synchronized(lock) {
            if (closed) return
            conversationWindow.clear()
            if (sendingGeneration != null) {
                discardAfterSend = true
                null
            } else {
                activeConversation.also {
                    activeConversation = null
                    conversationTurnCount = 0
                }
            }
        }
        if (detached != null) {
            // Queue cleanup behind any completing model callback and before a new
            // call turn's sendMessage. Never close native Conversation mid-callback.
            try {
                executor.execute {
                    closeConversation(detached)
                    closeIsolatedEngineWhenIdle()
                }
            } catch (_: RejectedExecutionException) {
                closeConversation(detached)
                closeIsolatedEngineWhenIdle()
            }
        }
    }

    override fun close() {
        val idleConversation: Conversation?
        val idleEngine: Engine?
        val inFlight: Conversation?
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            conversationWindow.clear()
            if (sendingGeneration != null) {
                discardAfterSend = true
                inFlight = activeConversation
                idleConversation = null
                idleEngine = null
            } else {
                inFlight = null
                idleConversation = activeConversation
                idleEngine = engine
                activeConversation = null
                engine = null
            }
        }
        try { inFlight?.cancelProcess() } catch (_: Throwable) {}
        if (idleConversation != null || idleEngine != null) {
            try {
                executor.execute {
                    closeConversation(idleConversation)
                    closeEngine(idleEngine)
                }
            } catch (_: RejectedExecutionException) {
                closeConversation(idleConversation)
                closeEngine(idleEngine)
            }
        }
        executor.shutdown()
    }

    private fun runGeneration(
        userText: String,
        requestGeneration: Long,
        listener: TextCallAgentBackend.Listener,
        audioPcm: ByteArray?,
    ) {
        if (!isCurrent(requestGeneration)) { audioPcm?.fill(0); return }

        var conversation: Conversation? = null
        var registeredConversation = false
        var successfulSend = false
        var callbackDelivered = false
        var reused = false
        var turnIndex = 0
        val startedNs = System.nanoTime()
        var engineReadyNs = startedNs
        var conversationReadyNs = startedNs
        var inferenceReadyNs = startedNs
        var outputChars = 0
        var status = "cancelled"
        var contextRecoveries = 0
        var contextRecoveryAttempts = 0
        var nativeContextTokens: Int? = null
        var replyRecoveryAttempts = 0
        var replyValidationFailure = "none"
        var repairingReply = false
        try {
            var readyEngine = ensureEngine()
            engineReadyNs = System.nanoTime()
            if (!isCurrent(requestGeneration)) return

            val cached = synchronized(lock) {
                if (!isCurrentLocked(requestGeneration)) return
                activeConversation
            }
            if (cached != null) {
                conversation = cached
                registeredConversation = true
                reused = true
            } else {
                conversation = createConversation(readyEngine)
                synchronized(lock) {
                    if (!isCurrentLocked(requestGeneration)) return
                    activeConversation = conversation
                    conversationTurnCount = conversationWindow.completedTurns
                    registeredConversation = true
                }
            }
            conversationReadyNs = System.nanoTime()
            synchronized(lock) {
                if (!isCurrentLocked(requestGeneration)) return
                sendingGeneration = requestGeneration
                turnIndex = conversationTurnCount + 1
            }

            fun recreate(failed: Conversation): Conversation {
                synchronized(lock) {
                    if (!isCurrentLocked(requestGeneration))
                        throw CancellationException("native_generation_cancelled")
                    if (activeConversation === failed) activeConversation = null
                    if (sendingGeneration == requestGeneration) sendingGeneration = null
                }
                closeConversation(failed)
                closeIsolatedEngineWhenIdle()
                readyEngine = ensureEngine()
                val replacement = createConversation(readyEngine, repairingReply)
                conversation = replacement
                registeredConversation = false
                synchronized(lock) {
                    if (!isCurrentLocked(requestGeneration))
                        throw CancellationException("native_generation_cancelled")
                    activeConversation = replacement
                    sendingGeneration = requestGeneration
                    registeredConversation = true
                }
                reused = false
                return replacement
            }
            val message = sendWithReplyRecovery(
                isCurrent = { isCurrent(requestGeneration) },
                validate = { candidate ->
                    if (validateConversationReply)
                        ConversationReplyProtocol.validateGenerated(candidate.toString().trim(), audioPcm != null)
                },
                rejected = { replyValidationFailure = it.name.lowercase() },
                recreateForRepair = {
                    repairingReply = true
                    replyRecoveryAttempts++
                    var reclaimedForRepair = false
                    recreateWithContextRecovery(
                        window = conversationWindow,
                        isCurrent = { isCurrent(requestGeneration) },
                        recreate = { recreate(checkNotNull(conversation)) },
                        onCapacityRetry = {
                            contextRecoveryAttempts++
                            reclaimedForRepair = true
                        },
                    )
                    if (reclaimedForRepair) contextRecoveries++
                },
                send = {
                    sendWithContextRecovery(
                        initial = checkNotNull(conversation),
                        window = conversationWindow,
                        isCurrent = { isCurrent(requestGeneration) },
                        replaceConversation = { failed ->
                            contextRecoveryAttempts++
                            val replacement = recreate(failed)
                            contextRecoveries++
                            replacement
                        },
                        send = { candidate ->
                            // Diagnostic callback has no authority and receives no conversation text.
                            runCatching { onNativeSendEntered() }
                            if (audioPcm == null) candidate.sendMessage(
                                text = userText,
                                maxOutputToken = textOutputTokenBudget,
                                thinkingConfig = ThinkingConfig(enableThinking = false),
                                responseFormat = responseFormat,
                            ) else {
                                val wave = pl.michalmatu.aicallbridge.audioengines.GemmaAudioPcm16.toWave(audioPcm)
                                try {
                                    candidate.sendMessage(
                                        contents = Contents.of(Content.Text(userText), Content.AudioBytes(wave)),
                                        maxOutputToken = AUDIO_OUTPUT_TOKENS,
                                        thinkingConfig = ThinkingConfig(enableThinking = false),
                                        responseFormat = audioResponseFormat,
                                    )
                                } finally { wave.fill(0) }
                            }
                        },
                    )
                },
            )
            nativeContextTokens = runCatching { conversation?.getTokenCount() }.getOrNull()
            inferenceReadyNs = System.nanoTime()
            val text = message.toString().trim()
            outputChars = text.length
            synchronized(lock) {
                if (sendingGeneration == requestGeneration) sendingGeneration = null
                if (isCurrentLocked(requestGeneration) && text.isNotBlank()) {
                    conversationTurnCount += 1
                    if (audioPcm == null && !discardAfterSend) conversationWindow.remember(userText, text)
                    successfulSend = true
                }
            }
            if (text.isBlank()) {
                status = "empty"
                if (isCurrent(requestGeneration)) listener.onError("empty_response")
            } else if (successfulSend && isCurrent(requestGeneration)) {
                status = "complete"
                callbackDelivered = true
                listener.onComplete(text)
            }
        } catch (error: Throwable) {
            inferenceReadyNs = System.nanoTime()
            status = "error_${error.javaClass.simpleName}_${nativeStatusCode(error)}_${nativeFailureHints(error)}"
            nativeContextTokens = runCatching { conversation?.getTokenCount() }.getOrNull()
            successfulSend = false
            if (isCurrent(requestGeneration)) listener.onError(
                if (error is InvalidConversationReply) "conversation_reply_invalid" else sanitizeRuntimeError(error))
        } finally {
            audioPcm?.fill(0)
            // Only timing/accelerator metadata. Never print transcripts, history,
            // prompt, model reply, phone details, model file path or personal data.
            val nativeCancelRequested = synchronized(lock) {
                nativeCancelRequestedGeneration == requestGeneration
            }
            val timing = NativeModelTiming(
                backend = activeBackendLabel,
                contextTokens = contextTokenBudget,
                engineReadyMs = ((engineReadyNs - startedNs) / 1_000_000).coerceAtLeast(0),
                conversationCreateMs =
                    ((conversationReadyNs - engineReadyNs) / 1_000_000).coerceAtLeast(0),
                generateMs =
                    ((inferenceReadyNs - conversationReadyNs) / 1_000_000).coerceAtLeast(0),
                conversationReused = reused,
                turn = turnIndex,
                outputChars = outputChars,
                status = status,
                nativeCancelRequested = nativeCancelRequested,
                contextRecoveries = contextRecoveries,
                contextRecoveryAttempts = contextRecoveryAttempts,
                nativeContextTokens = nativeContextTokens,
                retainedContextTurns = conversationWindow.snapshot().size,
                replyRecoveryAttempts = replyRecoveryAttempts,
                replyValidationFailure = replyValidationFailure,
            )
            Log.i("AiCallBridgePerf", "gemma4_runtime_backend=${timing.backend}" +
                ",engine_ready_ms=${timing.engineReadyMs}" +
                ",conversation_create_ms=${timing.conversationCreateMs}" +
                ",generate_ms=${timing.generateMs}" +
                ",conversation_reused=${timing.conversationReused},conversation_turn=${timing.turn}" +
                ",output_chars=${timing.outputChars},status=${timing.status}" +
                ",context_token_budget=${timing.contextTokens}" +
                ",native_cancel_requested=${timing.nativeCancelRequested}" +
                ",context_recoveries=${timing.contextRecoveries}" +
                ",context_recovery_attempts=${timing.contextRecoveryAttempts}" +
                ",native_context_tokens=${timing.nativeContextTokens ?: -1}" +
                ",retained_context_turns=${timing.retainedContextTurns}" +
                ",reply_recovery_attempts=${timing.replyRecoveryAttempts}" +
                ",reply_validation_failure=${timing.replyValidationFailure}")
            // Callback is strictly diagnostic: it cannot alter the native model outcome.
            runCatching { onTiming(timing) }

            val toClose = synchronized(lock) {
                if (sendingGeneration == requestGeneration) sendingGeneration = null
                if (nativeCancelRequestedGeneration == requestGeneration) {
                    nativeCancelRequestedGeneration = null
                }
                when {
                    // A conversation created just before cancellation never became
                    // owned by the runtime and must be closed in this worker.
                    !registeredConversation -> conversation
                    // resetConversationContext or close has already detached it.
                    activeConversation !== conversation -> null
                    closed || !successfulSend || !callbackDelivered || discardAfterSend -> {
                        activeConversation = null
                        conversationTurnCount = 0
                        discardAfterSend = false
                        conversation
                    }
                    else -> null // Same active, healthy chat reused next turn.
                }
            }
            closeConversation(toClose)
            if (toClose != null) closeIsolatedEngineWhenIdle()
            closeEngineIfRuntimeClosed()
        }
    }

    private val textOutputTokenBudget get() =
        if (validateConversationReply) STRUCTURED_OUTPUT_TOKENS else MAX_OUTPUT_TOKENS

    /** Role-labelled history remains untrusted conversation data, never system authority. */
    private fun createConversation(readyEngine: Engine, repair: Boolean = false): Conversation =
        readyEngine.createConversation(ConversationConfig(
            systemInstruction = (if (repair) systemInstruction.orEmpty() + "\n" +
                ConversationReplyRecovery.REPAIR_INSTRUCTION else systemInstruction)?.let(Contents::of),
            initialMessages = conversationWindow.snapshot().flatMap {
                listOf(Message.user(it.userText), Message.model(it.modelText))
            },
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
            automaticToolCalling = false,
            extraContext = if (disableQwenThinking) mapOf("enable_thinking" to false) else emptyMap(),
            channels = emptyList(),
            maxOutputToken = if (audioResponseFormat != null) AUDIO_OUTPUT_TOKENS else textOutputTokenBudget,
            thinkingConfig = ThinkingConfig(enableThinking = false),
            enableResponseFormat = responseFormat != null,
        ))

    private fun closeConversation(value: Conversation?) {
        try { value?.close() } catch (_: Throwable) {}
    }

    /** LiteRT-LM 0.19.0 can retain Qwen3.5 recurrent state after closing a Conversation. */
    private fun closeIsolatedEngineWhenIdle() {
        if (!executionPolicy.newEngineAfterContextReset) return
        val detached = synchronized(lock) {
            if (sendingGeneration != null || activeConversation != null) null
            else engine.also { engine = null }
        }
        closeEngine(detached)
    }

    private fun ensureEngine(): Engine {
        synchronized(lock) {
            engine?.let { return it }
            check(!closed) { "runtime_closed" }
        }
        val file = File(modelPath)
        require(file.isFile && file.length() > 0L) { "model_missing" }

        val initialized = if (executionPolicy.cpuOnly) {
            // Exynos S22/Mali 8 GB: avoid large GPU allocation reported for Qwen3.5 2B.
            initializeEngine(Backend.CPU(threadCount = cpuThreadCount()))
                .also { activeBackendLabel = "CPU" }
        } else try {
            initializeEngine(Backend.GPU()).also { activeBackendLabel = "GPU" }
        } catch (error: Throwable) {
            Log.w("AiCallBridgePerf", "gemma4_gpu_fallback_reason=${error.javaClass.simpleName}")
            initializeEngine(Backend.CPU(threadCount = cpuThreadCount()))
                .also { activeBackendLabel = "CPU" }
        }

        synchronized(lock) {
            if (closed) {
                closeEngine(initialized)
                throw IllegalStateException("runtime_closed")
            }
            val existing = engine
            if (existing != null) {
                closeEngine(initialized)
                return existing
            }
            engine = initialized
            return initialized
        }
    }

    private fun initializeEngine(backend: Backend): Engine {
        val modelFile = File(modelPath)
        val cacheDirectory = File(modelFile.parentFile ?: modelFile.absoluteFile.parentFile, ".litert-cache")
        cacheDirectory.mkdirs()
        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                visionBackend = null,
                audioBackend = if (audioResponseFormat != null) Backend.CPU() else null,
                maxNumTokens = contextTokenBudget,
                cacheDir = cacheDirectory.absolutePath,
            ),
        )
        try {
            candidate.initialize()
            return candidate
        } catch (error: Throwable) {
            closeEngine(candidate)
            throw error
        }
    }

    private fun closeEngineIfRuntimeClosed() {
        val engineToClose = synchronized(lock) {
            if (!closed || activeConversation != null) return
            engine.also { engine = null }
        }
        closeEngine(engineToClose)
    }

    private fun closeEngine(candidate: Engine?) {
        if (candidate == null || !candidate.isInitialized()) return
        try {
            candidate.close()
        } catch (_: Throwable) {
            // Best-effort native teardown.
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        isCurrentLocked(requestGeneration)
    }

    private fun isCurrentLocked(requestGeneration: Long): Boolean =
        !closed && generation == requestGeneration

    private fun sanitizeRuntimeError(error: Throwable): String {
        val message = error.message
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.replace('=', ':')
            ?.take(MAX_ERROR_CHARS)
            ?.ifBlank { null }
        return if (message == null) {
            error.javaClass.simpleName
        } else {
            "${error.javaClass.simpleName}:$message"
        }
    }

    private fun cpuThreadCount(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(MIN_CPU_THREADS, MAX_CPU_THREADS)

    private companion object {
        const val MAX_OUTPUT_TOKENS = 128
        const val STRUCTURED_OUTPUT_TOKENS = 512
        const val AUDIO_OUTPUT_TOKENS = 512
        const val MAX_ERROR_CHARS = 140
        const val MIN_CPU_THREADS = 2
        const val MAX_CPU_THREADS = 6
    }
}

internal object Gemma4LiteRtTextBackendFactory {
    /** Uses the same native lifecycle without changing the selected profile or starting a Call. */
    fun createCompatibilityProbe(file: File, audio: Boolean): TextCallAgentBackend =
        Gemma4LiteRtTextBackend(Gemma4RuntimeFactory { path, instruction ->
            LiteRtGemma4Runtime(path, instruction,
                responseFormat = ResponseFormat.json(ConversationReplyProtocol.schema(emptySet())),
                audioResponseFormat = if (audio) ResponseFormat.json(
                    ConversationReplyProtocol.audioSchema(emptySet())) else null)
        }, file.absolutePath, ConversationReplyProtocol.INSTRUCTION)

    fun modelFile(context: Context, profile: pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile): File {
        if (profile.dialogue != DialogueModelId.USER_LITERT) return modelFile(context, profile.dialogue)
        val library = UserLiteRtModelStore(context)
        return library.file(checkNotNull(library.find(profile.customModelId)) { "user_model_missing" })
    }

    fun createConversationReply(
        context: Context,
        systemInstruction: String,
        modelId: DialogueModelId,
        tools: Set<ConversationToolId>,
        contextTokenBudget: Int = if (modelId == DialogueModelId.GEMMA_4_E2B) 4_096 else DEFAULT_CONTEXT_TOKENS,
        onTiming: (NativeModelTiming) -> Unit = {},
        audioInput: Boolean = false,
        selectedFile: File? = null,
    ): TextCallAgentBackend = createInternal(
        context = context, modelId = modelId, systemInstruction = systemInstruction,
        responseFormat = ResponseFormat.json(ConversationReplyProtocol.schema(tools)),
        contextTokenBudget = contextTokenBudget,
        onTiming = onTiming,
        validateConversationReply = true,
        selectedFile = selectedFile,
        audioResponseFormat = if (audioInput) ResponseFormat.json(ConversationReplyProtocol.audioSchema(tools)) else null,
    )

    private fun selectedModel(context: Context): DialogueModelId =
        checkNotNull(AudioEngineProfileStore(context).load()) { "invalid_audio_engine_profile" }.dialogue

    fun modelFile(
        context: Context,
        modelId: DialogueModelId = selectedModel(context),
    ): File {
        val appContext = context.applicationContext
        val directory = appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")
        directory.mkdirs()
        if (modelId == DialogueModelId.USER_LITERT) {
            val profile = checkNotNull(AudioEngineProfileStore(appContext).load()) {
                "invalid_audio_engine_profile"
            }
            require(profile.dialogue == DialogueModelId.USER_LITERT) { "user_model_not_selected" }
            val library = UserLiteRtModelStore(appContext)
            val model = checkNotNull(library.find(profile.customModelId)) { "user_model_missing" }
            return library.file(model)
        }
        return File(directory, LanguageModelCatalog[modelId].spec.fileName)
    }

    fun create(
        context: Context,
        systemInstruction: String? = LocalPhoneLlmBackendFactory.SYSTEM_PROMPT,
        modelId: DialogueModelId = selectedModel(context),
    ): TextCallAgentBackend = createInternal(
        context = context,
        modelId = modelId,
        systemInstruction = systemInstruction,
        responseFormat = null,
    )

    fun createSkillClassifier(
        context: Context,
        systemInstruction: String,
        allowedSkills: Set<DialogueSkillId>,
        modelId: DialogueModelId = selectedModel(context),
    ): TextCallAgentBackend {
        require(allowedSkills.isNotEmpty()) { "dialogue_skill_set_must_not_be_empty" }
        val allowed = allowedSkills.sortedBy { it.name }.joinToString(",") { "\"${it.name}\"" }
        val schema = """
            {
              "type":"object",
              "properties":{
                "skill":{"type":"string","enum":[$allowed]},
                "confidence":{"type":"number","minimum":0,"maximum":1},
                "reason":{"type":"string","maxLength":160}
              },
              "required":["skill","confidence"],
              "additionalProperties":false
            }
        """.trimIndent()
        return createInternal(
            context = context,
            modelId = modelId,
            systemInstruction = systemInstruction,
            responseFormat = ResponseFormat.json(schema),
        )
    }

    /**
     * F-046 research flow: free-form conversational speech; no JSON response schema.
     * Direct mode does not grant any tool, disclosure or external-effect permission.
     */
    fun createDirectDialogue(
        context: Context,
        systemInstruction: String,
        modelId: DialogueModelId = selectedModel(context),
    ): TextCallAgentBackend = createInternal(
        context = context,
        modelId = modelId,
        systemInstruction = systemInstruction,
        responseFormat = null,
    )

    fun createActionClassifier(
        context: Context,
        systemInstruction: String,
        allowedActions: Set<DialogueActionId>,
        modelId: DialogueModelId = selectedModel(context),
    ): Gemma4LiteRtTextBackend {
        require(allowedActions.isNotEmpty()) { "dialogue_action_set_must_not_be_empty" }
        val allowed = allowedActions.sortedBy { it.name }.joinToString(",") { "\"${it.name}\"" }
        val schema = """
            {
              "type":"object",
              "properties":{
                "action":{"type":"string","enum":[$allowed]},
                "confidence":{"type":"number","minimum":0,"maximum":1},
                "argument":{"type":"string","maxLength":80},
                "reason":{"type":"string","maxLength":160}
              },
              "required":["action","confidence"],
              "additionalProperties":false
            }
        """.trimIndent()
        return createInternal(
            context = context,
            modelId = modelId,
            systemInstruction = systemInstruction,
            responseFormat = ResponseFormat.json(schema),
        )
    }

    private fun createInternal(
        context: Context,
        systemInstruction: String?,
        responseFormat: ResponseFormat?,
        audioResponseFormat: ResponseFormat? = null,
        selectedFile: File? = null,
        modelId: DialogueModelId,
        contextTokenBudget: Int = DEFAULT_CONTEXT_TOKENS,
        onTiming: (NativeModelTiming) -> Unit = {},
        validateConversationReply: Boolean = false,
    ): Gemma4LiteRtTextBackend {
        val modelPath = (selectedFile ?: modelFile(context, modelId)).absolutePath
        return Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { path, instruction ->
                LiteRtGemma4Runtime(
                    modelPath = path,
                    systemInstruction = instruction,
                    responseFormat = responseFormat,
                    audioResponseFormat = audioResponseFormat,
                    disableQwenThinking = modelId == DialogueModelId.QWEN_3_1_7B || modelId == DialogueModelId.QWEN_3_0_6B || modelId == DialogueModelId.QWEN_3_5_2B,
                    executionPolicy = modelId.nativeExecutionPolicy(),
                    contextTokenBudget = contextTokenBudget,
                    onTiming = onTiming,
                    validateConversationReply = validateConversationReply,
                )
            },
            modelPath = modelPath,
            systemInstruction = systemInstruction,
        )
    }
}
