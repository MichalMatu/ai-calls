package pl.michalmatu.aicallbridge.developerrelay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Safe physical phone-local model -> skill -> exact approved speech contract.
 * Synthetic input; no phone call, external effect, microphone or speaker.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSupervisedGemmaEndToEndContractTest {
    @Test
    fun supervisedGemmaProducesOnlyAppOwnedSpeechOnRealDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelReplies = AtomicInteger()
        val fallbackReplies = AtomicInteger()
        val reply = AtomicReference<String?>()
        val error = AtomicReference<String?>()
        val done = CountDownLatch(1)
        val backend = SupervisedGemmaDialogueBackend(
            context = context,
            onModelReply = { modelReplies.incrementAndGet() },
            onFallbackReply = { fallbackReplies.incrementAndGet() },
        )
        try {
            backend.generate(
                "Dzień dobry, chciałem sprawdzić, czy mnie słychać.",
                object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        reply.set(text)
                        done.countDown()
                    }

                    override fun onError(reason: String) {
                        error.set(reason)
                        done.countDown()
                    }
                },
            )
            assertTrue("Supervised Gemma timed out", done.await(100, TimeUnit.SECONDS))
            assertEquals("Supervised backend returned error", null, error.get())
            val spoken = reply.get()
            assertNotNull("No speech candidate", spoken)
            assertEquals(
                "Unexpected model text escaped the reviewed output policy",
                spoken,
                SupervisedGemmaSpeechPolicy.review(checkNotNull(spoken)),
            )
            println(
                "SUPERVISED_GEMMA_ROUTE model=${modelReplies.get()} fallback=${fallbackReplies.get()} approved_chars=${spoken?.length}",
            )
            assertEquals("Real Gemma did not produce an approved skill", 1, modelReplies.get())
            assertEquals("Unexpected script fallback", 0, fallbackReplies.get())
        } finally {
            backend.close()
        }
    }
}
