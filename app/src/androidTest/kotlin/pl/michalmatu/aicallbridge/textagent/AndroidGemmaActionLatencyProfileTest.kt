package pl.michalmatu.aicallbridge.textagent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.google.gson.JsonParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.GateCHybridDialogueBackendFactory

/**
 * On-device, synthetic-only Gemma action benchmark. No call, mic, identity, TTS, effect, or grant.
 * Prints latency/backend readiness diagnostics but NEVER the transcript or model reply.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaActionLatencyProfileTest {
    @Test
    fun measureTwoConsecutiveStructuredActionInferencesWithoutPhoneCall() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val policy = GateCHybridDialogueBackendFactory.actionPolicy(true)
        val classifier = Gemma4LiteRtTextBackendFactory.createActionClassifier(
            context = app,
            systemInstruction = DialogueActionCatalog.systemPrompt(
                policy,
                GateCHybridDialogueBackendFactory.actionContext(true),
            ),
            allowedActions = policy.allowedActions,
        )
        try {
            val samples = listOf(
                "W jakiej sprawie pan dzwoni?",
                "Jaka informacja jest potrzebna, żeby włączyć usługę?",
            )
            samples.forEachIndexed { index, syntheticText ->
                val done = CountDownLatch(1)
                val result = AtomicReference<String?>(null)
                val error = AtomicReference<String?>(null)
                val started = System.nanoTime()
                classifier.generate(syntheticText, object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        result.set(text)
                        done.countDown()
                    }

                    override fun onError(reason: String) {
                        error.set(reason)
                        done.countDown()
                    }
                })
                assertTrue("Gemma benchmark timed out", done.await(120, TimeUnit.SECONDS))
                val elapsed = (System.nanoTime() - started) / 1_000_000L
                val response = result.get().orEmpty()
                val validJson = runCatching {
                    val parsed = JsonParser.parseString(response).asJsonObject
                    parsed.has("action") && parsed.has("confidence") &&
                        parsed.get("action").asString in
                            policy.allowedActions.map { it.name }
                }.getOrDefault(false)
                println("GEMMA_ACTION_PROFILE_TURN_${index + 1}_MS=$elapsed")
                println("GEMMA_ACTION_PROFILE_TURN_${index + 1}_VALID_JSON=$validJson")
                println("GEMMA_ACTION_PROFILE_TURN_${index + 1}_ERROR_CLASS=" +
                    (if (error.get() == null) "none" else "runtime_error"))
                assertTrue("Gemma synthetic action JSON is valid", validJson)
            }
        } finally {
            classifier.close()
        }
    }
}
