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
import pl.michalmatu.aicallbridge.campaign.ClirCampaignSpec
import pl.michalmatu.aicallbridge.campaign.GemmaDirectPromptSkillCatalog
import pl.michalmatu.aicallbridge.campaign.GemmaDirectTaskBriefBuilder

/**
 * Strictly off-call native model contract. NEVER dials, captures audio, speaks,
 * opens identity storage or changes app authorization. Prints metadata only.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGemmaDirectOnDeviceTest {
    @Test fun fourSyntheticGemmaDirectTurnsReturnNaturalText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timestamp = System.currentTimeMillis()
        val scope = "synthetic-test-scope"
        val grant = ClirCampaignSpec.acceptEnableTask(scope, timestamp)
        val instruction = GemmaDirectTaskBriefBuilder.build(
            ClirCampaignSpec.enableRequest(scope),
            grant,
            GemmaDirectPromptSkillCatalog.defaults,
        )
        val model = Gemma4LiteRtTextBackendFactory.createDirectDialogue(
            context, instruction,
        )
        try {
            val syntheticTurns = listOf(
                "Dzień dobry, w jakiej sprawie Pan dzwoni?",
                "Rozumiem. Czego dokładnie Pan potrzebuje od operatora?",
                "Nie udało mi się znaleźć tej informacji. Co możemy zrobić dalej?",
                "Czy może Pan przypomnieć cel tej rozmowy?",
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
                val raw = response.get()?.trim().orEmpty()
                val usable = raw.isNotEmpty() && raw.length <= 1024 &&
                    !raw.contains("[[") && !raw.contains("]]")
                println("DIRECT_OFFCALL_TURN_${index + 1}_MS=" +
                    ((System.nanoTime() - started) / 1_000_000L))
                println("DIRECT_OFFCALL_TURN_${index + 1}_RAW_USABLE=" + usable)
                println("DIRECT_OFFCALL_TURN_${index + 1}_OUTPUT_CHARS=" + raw.length)
                println("DIRECT_OFFCALL_TURN_${index + 1}_RUNTIME_ERROR=" + (error.get() != null))
                assertTrue("Gemma Direct raw speech text valid on S22", usable)
            }
        } finally {
            model.close()
        }
    }
}
