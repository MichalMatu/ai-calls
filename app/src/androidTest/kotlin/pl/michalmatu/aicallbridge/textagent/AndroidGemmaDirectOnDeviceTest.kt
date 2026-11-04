package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.developerrelay.GemmaDirectProposalParser
import pl.michalmatu.aicallbridge.developerrelay.GemmaDirectTaskBrief

/**
 * Strictly off-call native model contract. NEVER dials, captures audio, speaks,
 * opens identity storage or changes app authorization. Prints metadata only.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaDirectOnDeviceTest {
    @Test fun twoSyntheticGemmaDirectTurnsReturnStructuredSafeProposals() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = Gemma4LiteRtTextBackendFactory.createDirectDialogue(
            context, GemmaDirectTaskBrief.PROMPT,
        )
        try {
            val syntheticTurns = listOf(
                "Dzień dobry, w jakiej sprawie Pan dzwoni?",
                "Rozumiem. Czego dokładnie Pan potrzebuje od operatora?",
            )
            syntheticTurns.forEachIndexed { index, utterance ->
                val done = CountDownLatch(1)
                val response = AtomicReference<String?>(null)
                val error = AtomicReference<String?>(null)
                val started = System.nanoTime()
                model.generate(utterance, object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        response.set(text)
                        done.countDown()
                    }

                    override fun onError(reason: String) {
                        error.set(reason)
                        done.countDown()
                    }
                })
                assertTrue("Direct synthetic model timed out", done.await(120, TimeUnit.SECONDS))
                val accepted = response.get()?.let(GemmaDirectProposalParser::parse)
                println("DIRECT_OFFCALL_TURN_${index + 1}_MS=" +
                    ((System.nanoTime() - started) / 1_000_000L))
                println("DIRECT_OFFCALL_TURN_${index + 1}_PROPOSAL_VALID=" + (accepted != null))
                println("DIRECT_OFFCALL_TURN_${index + 1}_RUNTIME_ERROR=" + (error.get() != null))
                assertTrue("Gemma Direct JSON/action/speech valid on device", accepted != null)
            }
        } finally {
            model.close()
        }
    }
}
