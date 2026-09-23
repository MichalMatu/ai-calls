package pl.michalmatu.aicallbridge.textagent

import android.content.Context
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
    override fun close()
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
) : TextCallAgentBackend {
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
        val conversation = synchronized(lock) {
            if (closed) return
            generation += 1
            activeConversation
        }
        try {
            conversation?.cancelProcess()
        } catch (_: Throwable) {
            // Generation invalidation above is authoritative; native cancellation is best-effort.
        }
    }

    override fun close() {
        val conversation: Conversation?
        val engineToClose: Engine?
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            conversation = activeConversation
            engineToClose = if (conversation == null) engine.also { engine = null } else null
        }
        try {
            conversation?.cancelProcess()
        } catch (_: Throwable) {
            // The worker will finish/tear down stale work.
        }
        closeEngine(engineToClose)
        executor.shutdown()
    }

    private fun runGeneration(
        userText: String,
        requestGeneration: Long,
        listener: TextCallAgentBackend.Listener,
    ) {
        if (!isCurrent(requestGeneration)) return

        var conversation: Conversation? = null
        try {
            val readyEngine = ensureEngine()
            if (!isCurrent(requestGeneration)) return

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
            }

            val message = conversation.sendMessage(
                text = userText,
                maxOutputToken = MAX_OUTPUT_TOKENS,
                thinkingConfig = ThinkingConfig(enableThinking = false),
                responseFormat = responseFormat,
            )
            val text = message.toString().trim()
            if (text.isBlank()) {
                if (isCurrent(requestGeneration)) listener.onError("empty_response")
            } else if (isCurrent(requestGeneration)) {
                listener.onComplete(text)
            }
        } catch (error: Throwable) {
            if (isCurrent(requestGeneration)) {
                listener.onError(sanitizeRuntimeError(error))
            }
        } finally {
            val conversationToClose = synchronized(lock) {
                if (activeConversation === conversation) activeConversation = null
                conversation
            }
            try {
                conversationToClose?.close()
            } catch (_: Throwable) {
                // Best-effort native teardown.
            }
            closeEngineIfRuntimeClosed()
        }
    }

    private fun ensureEngine(): Engine {
        synchronized(lock) {
            engine?.let { return it }
            check(!closed) { "runtime_closed" }
        }
        val file = File(modelPath)
        require(file.isFile && file.length() > 0L) { "model_missing" }

        val initialized = try {
            initializeEngine(Backend.GPU())
        } catch (_: Throwable) {
            initializeEngine(Backend.CPU(threadCount = cpuThreadCount()))
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

    private fun createInternal(
        context: Context,
        systemInstruction: String?,
        responseFormat: ResponseFormat?,
    ): TextCallAgentBackend {
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
