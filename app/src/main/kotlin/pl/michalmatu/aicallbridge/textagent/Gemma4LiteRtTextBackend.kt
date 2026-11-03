package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal fun interface Gemma4RuntimeFactory {
    fun create(modelPath: String, systemInstruction: String?): Gemma4Runtime
}

internal interface Gemma4Runtime : AutoCloseable {
    fun generate(userText: String, listener: TextCallAgentBackend.Listener)
    fun cancel()
    /** Drop only synthetic prewarm history, not the initialized model engine. */
    fun resetConversationContext() = Unit
    override fun close()
}

/** Model-native chat context persists per owned call instead of prompt-duplicating recent turns. */
internal interface NativeConversationContextBackend {
    fun resetConversationContext()
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
    private val systemInstruction: String? = null,
) : TextCallAgentBackend, NativeConversationContextBackend {
    private val lock = Any()
    private var generation = 0L
    private var runtime: Gemma4Runtime? = null
    private var closed = false

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }

        val activeRuntime: Gemma4Runtime
        val requestGeneration: Long
        synchronized(lock) {
            check(!closed) { "gemma4_backend_closed" }
            generation += 1
            requestGeneration = generation
            val existing = runtime
            if (existing == null) {
                activeRuntime = runtimeFactory.create(modelPath, systemInstruction)
                runtime = activeRuntime
            } else {
                existing.cancel()
                activeRuntime = existing
            }
        }

        try {
            activeRuntime.generate(userText, guardedListener(requestGeneration, listener))
        } catch (error: Throwable) {
            if (isCurrent(requestGeneration)) {
                listener.onError("gemma4_runtime_${sanitize(error.javaClass.simpleName)}")
            }
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
    private val systemInstruction: String?,
    private val responseFormat: ResponseFormat? = null,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aicall-gemma4-litert").apply { isDaemon = true }
    },
) : Gemma4Runtime {
    private val lock = Any()
    private var generation = 0L
    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    /** A normal between-turn cancel invalidates a callback but must not cancel native chat. */
    private var sendingGeneration: Long? = null
    private var discardAfterSend = false
    private var conversationTurnCount = 0
    @Volatile private var activeBackendLabel = "unknown"
    private var closed = false

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        require(userText.isNotBlank()) { "user_text_must_not_be_blank" }
        val requestGeneration = synchronized(lock) {
            check(!closed) { "runtime_closed" }
            generation += 1
            generation
        }
        try {
            executor.execute {
                runGeneration(userText, requestGeneration, listener)
            }
        } catch (_: RejectedExecutionException) {
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

    override fun resetConversationContext() {
        // Only the synthetic prewarm turn uses this. Keep the expensive Engine alive.
        val detached = synchronized(lock) {
            if (closed) return
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
                executor.execute { closeConversation(detached) }
            } catch (_: RejectedExecutionException) {
                closeConversation(detached)
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
    ) {
        if (!isCurrent(requestGeneration)) return

        var conversation: Conversation? = null
        var registeredConversation = false
        var successfulSend = false
        var reused = false
        var turnIndex = 0
        val startedNs = System.nanoTime()
        var engineReadyNs = startedNs
        var conversationReadyNs = startedNs
        var inferenceReadyNs = startedNs
        var outputChars = 0
        var status = "cancelled"
        try {
            val readyEngine = ensureEngine()
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
                conversation = readyEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = systemInstruction?.let(Contents::of),
                        samplerConfig = SamplerConfig(
                            topK = 1,
                            topP = 1.0,
                            temperature = 0.0,
                            seed = 0,
                        ),
                        automaticToolCalling = false,
                        channels = emptyList(),
                        maxOutputToken = MAX_OUTPUT_TOKENS,
                        thinkingConfig = ThinkingConfig(enableThinking = false),
                        enableResponseFormat = responseFormat != null,
                    ),
                )
                synchronized(lock) {
                    if (!isCurrentLocked(requestGeneration)) return
                    activeConversation = conversation
                    conversationTurnCount = 0
                    registeredConversation = true
                }
            }
            conversationReadyNs = System.nanoTime()
            val currentConversation = checkNotNull(conversation)
            synchronized(lock) {
                if (!isCurrentLocked(requestGeneration)) return
                sendingGeneration = requestGeneration
                turnIndex = conversationTurnCount + 1
            }

            val message = currentConversation.sendMessage(
                text = userText,
                maxOutputToken = MAX_OUTPUT_TOKENS,
                thinkingConfig = ThinkingConfig(enableThinking = false),
                responseFormat = responseFormat,
            )
            inferenceReadyNs = System.nanoTime()
            val text = message.toString().trim()
            outputChars = text.length
            synchronized(lock) {
                if (sendingGeneration == requestGeneration) sendingGeneration = null
                if (isCurrentLocked(requestGeneration) && text.isNotBlank()) {
                    conversationTurnCount += 1
                    successfulSend = true
                }
            }
            if (text.isBlank()) {
                status = "empty"
                if (isCurrent(requestGeneration)) listener.onError("empty_response")
            } else if (successfulSend && isCurrent(requestGeneration)) {
                status = "complete"
                listener.onComplete(text)
            }
        } catch (error: Throwable) {
            status = "error_${error.javaClass.simpleName}"
            successfulSend = false
            if (isCurrent(requestGeneration)) listener.onError(sanitizeRuntimeError(error))
        } finally {
            // Only timing/accelerator metadata. Never print transcripts, history,
            // prompt, model reply, phone details, model file path or personal data.
            Log.i("AiCallBridgePerf", "gemma4_runtime_backend=$activeBackendLabel" +
                ",engine_ready_ms=${((engineReadyNs - startedNs) / 1_000_000).coerceAtLeast(0)}" +
                ",conversation_create_ms=${((conversationReadyNs - engineReadyNs) / 1_000_000).coerceAtLeast(0)}" +
                ",generate_ms=${((inferenceReadyNs - conversationReadyNs) / 1_000_000).coerceAtLeast(0)}" +
                ",conversation_reused=$reused,conversation_turn=$turnIndex" +
                ",output_chars=$outputChars,status=$status")

            val toClose = synchronized(lock) {
                if (sendingGeneration == requestGeneration) sendingGeneration = null
                when {
                    // A conversation created just before cancellation never became
                    // owned by the runtime and must be closed in this worker.
                    !registeredConversation -> conversation
                    // resetConversationContext or close has already detached it.
                    activeConversation !== conversation -> null
                    closed || !successfulSend || discardAfterSend -> {
                        activeConversation = null
                        conversationTurnCount = 0
                        discardAfterSend = false
                        conversation
                    }
                    else -> null // Same active, healthy chat reused next turn.
                }
            }
            closeConversation(toClose)
            closeEngineIfRuntimeClosed()
        }
    }

    private fun closeConversation(value: Conversation?) {
        try { value?.close() } catch (_: Throwable) {}
    }

    private fun ensureEngine(): Engine {
        synchronized(lock) {
            engine?.let { return it }
            check(!closed) { "runtime_closed" }
        }
        val file = File(modelPath)
        require(file.isFile && file.length() > 0L) { "model_missing" }

        val initialized = try {
            initializeEngine(Backend.GPU()).also { activeBackendLabel = "GPU" }
        } catch (error: Throwable) {
            // A silent GPU→CPU fallback would otherwise look like a mysterious
            // "slow small model". Log only the exception class (no private data).
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
                audioBackend = null,
                maxNumTokens = MAX_CONTEXT_TOKENS,
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
        const val MAX_CONTEXT_TOKENS = 2_048
        const val MAX_OUTPUT_TOKENS = 128
        const val MAX_ERROR_CHARS = 140
        const val MIN_CPU_THREADS = 2
        const val MAX_CPU_THREADS = 6
    }
}

internal object Gemma4LiteRtTextBackendFactory {
    const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

    fun modelFile(context: Context): File {
        val appContext = context.applicationContext
        val directory = appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")
        directory.mkdirs()
        return File(directory, MODEL_FILE_NAME)
    }

    fun create(
        context: Context,
        systemInstruction: String? = LocalPhoneLlmBackendFactory.SYSTEM_PROMPT,
    ): TextCallAgentBackend = createInternal(
        context = context,
        systemInstruction = systemInstruction,
        responseFormat = null,
    )

    fun createSkillClassifier(
        context: Context,
        systemInstruction: String,
        allowedSkills: Set<DialogueSkillId>,
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
            systemInstruction = systemInstruction,
            responseFormat = ResponseFormat.json(schema),
        )
    }

    fun createActionClassifier(
        context: Context,
        systemInstruction: String,
        allowedActions: Set<DialogueActionId>,
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
            systemInstruction = systemInstruction,
            responseFormat = ResponseFormat.json(schema),
        )
    }

    private fun createInternal(
        context: Context,
        systemInstruction: String?,
        responseFormat: ResponseFormat?,
    ): Gemma4LiteRtTextBackend {
        val modelPath = modelFile(context).absolutePath
        return Gemma4LiteRtTextBackend(
            runtimeFactory = Gemma4RuntimeFactory { path, instruction ->
                LiteRtGemma4Runtime(
                    modelPath = path,
                    systemInstruction = instruction,
                    responseFormat = responseFormat,
                )
            },
            modelPath = modelPath,
            systemInstruction = systemInstruction,
        )
    }
}
