package pl.michalmatu.aicallbridge

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Fail-closed backend used only by the controlled Gate C physical diagnostic.
 *
 * The deterministic PhraseMatrix + CallPlan path must resolve the reviewed turn before backend
 * generation. Any generate() call is therefore a hard diagnostic failure rather than a model
 * fallback.
 */
internal class GateCFastPathSentinelBackend : TextCallAgentBackend {
    private val generateCallCount = AtomicInteger(0)
    private val closed = AtomicBoolean(false)

    val generateCalls: Int
        get() = generateCallCount.get()

    val isClosed: Boolean
        get() = closed.get()

    override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
        check(!closed.get()) { "gate_c_backend_closed" }
        generateCallCount.incrementAndGet()
        listener.onError("gate_c_backend_generate_forbidden")
    }

    override fun cancel() = Unit

    override fun close() {
        closed.set(true)
    }
}

internal data class GateCLiveCallFastPath(
    val scenario: GateCLiveCallScenario,
    val backend: GateCFastPathSentinelBackend,
    val maxCaptureMs: Int,
)

internal object GateCLiveCallFastPathFactory {
    private const val MAX_CAPTURE_MS = 15_000

    fun create(
        targetDialAddress: String,
        action: OrangeLiveAction = OrangeLiveAction.GREETING,
    ): GateCLiveCallFastPath = GateCLiveCallFastPath(
        scenario = GateCLiveCallScenarioFactory.create(targetDialAddress, action),
        backend = GateCFastPathSentinelBackend(),
        maxCaptureMs = MAX_CAPTURE_MS,
    )
}
