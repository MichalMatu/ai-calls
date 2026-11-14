package pl.michalmatu.aicallbridge.textagent

import com.google.ai.edge.litertlm.ResponseFormat
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversation.ConversationReplyProtocol

/** No model binary, SDK device or calls. The worker is intentionally never started
 * until the request is stale: we check the real runtime queue's PCM ownership.
 */
class QueuedGemmaAudioLifecycleTest {
    private class PausedExecutor : AbstractExecutorService() {
        private val pending = mutableListOf<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) {
            if (stopped) throw RejectedExecutionException()
            pending.add(command)
        }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> {
            stopped = true
            return pending.toMutableList().also { pending.clear() }
        }
        override fun isShutdown(): Boolean = stopped
        override fun isTerminated(): Boolean = stopped && pending.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated
        fun drain() {
            val copy = pending.toList()
            pending.clear()
            copy.forEach { it.run() }
        }
    }

    private class Result : TextCallAgentBackend.Listener {
        var callbacks = 0
        override fun onComplete(text: String) { callbacks++ }
        override fun onError(reason: String) { callbacks++ }
    }

    private fun runtime(executor: PausedExecutor) = LiteRtGemma4Runtime(
        modelPath = "/missing/synthetic-model.litertlm",
        systemInstruction = "synthetic queue test only",
        audioResponseFormat = ResponseFormat.json(
            ConversationReplyProtocol.audioSchema(emptySet())),
        executor = executor,
    )

    private fun pendingAudio(runtime: LiteRtGemma4Runtime): ByteArray? {
        val slotField = runtime.javaClass.getDeclaredField("queuedAudio").apply {
            isAccessible = true
        }
        val slot = slotField.get(runtime) as QueuedGemmaAudioInput
        val bytesField = slot.javaClass.getDeclaredField("pending").apply { isAccessible = true }
        return bytesField.get(slot) as ByteArray?
    }

    @Test fun cancelWipesQueuedPcmBeforeWorkerCanRun() {
        val executor = PausedExecutor()
        val runtime = runtime(executor)
        val original = ByteArray(32_000) { 0x55.toByte() }
        val result = Result()
        runtime.generateAudio(original, result)
        val copy = requireNotNull(pendingAudio(runtime))
        assertNotSame(original, copy)
        assertArrayEquals(original, copy)
        runtime.cancel()
        assertNull(pendingAudio(runtime))
        assertTrue(copy.all { it == 0.toByte() })
        assertTrue(original.all { it == 0x55.toByte() })
        executor.drain()
        assertEquals(0, result.callbacks)
        runtime.close()
    }

    @Test fun supersedingAudioAndCloseWipeBothPendingCopies() {
        val executor = PausedExecutor()
        val runtime = runtime(executor)
        val result = Result()
        runtime.generateAudio(ByteArray(64_000) { 0x11.toByte() }, result)
        val previous = requireNotNull(pendingAudio(runtime))
        runtime.generateAudio(ByteArray(96_000) { 0x22.toByte() }, result)
        val next = requireNotNull(pendingAudio(runtime))
        assertTrue(previous.all { it == 0.toByte() })
        assertTrue(next.any { it != 0.toByte() })
        runtime.close()
        assertNull(pendingAudio(runtime))
        assertTrue(next.all { it == 0.toByte() })
        executor.drain()
        assertEquals(0, result.callbacks)
    }
}
