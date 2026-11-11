package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.*

@RunWith(AndroidJUnit4::class)
class AndroidLanguageModelCatalogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private fun candidate(): DialogueModelId = DialogueModelId.valueOf(
        checkNotNull(InstrumentationRegistry.getArguments().getString("model_id")),
    )

    @Test fun importsCandidateThroughAppTransferService() {
        val id = candidate()
        val entry = LanguageModelCatalog[id]
        val source = File(context.getExternalFilesDir("model-import"), entry.spec.fileName)
        assertEquals(entry.spec.expectedBytes, source.length())
        ActivityScenario.launch(LanguageModelActivity::class.java).use {
            context.startForegroundService(Intent(context, LanguageModelTransferService::class.java)
                .putExtra(LanguageModelTransferService.MODEL, id.name)
                .putExtra(LanguageModelTransferService.URI, Uri.fromFile(source).toString()))
            awaitTransfer(id)
            assertTrue(LanguageModelTransferService.status.message.contains("SHA-256 zgodny"))
            assertEquals(Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(context, id).state)
        }
    }

    @Test fun downloadsSmallQwenFromCatalogAndSurvivesActivityRecreation() {
        val id = DialogueModelId.QWEN_3_0_6B
        assertNotEquals(Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(context, id).state)
        ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                button(activity.findViewById(android.R.id.content), "Pobierz Qwen3 0.6B").performClick()
            }
            scenario.recreate()
            awaitTransfer(id)
            assertTrue(LanguageModelTransferService.status.message, LanguageModelTransferService.status.message.contains("SHA-256 zgodny"))
            assertEquals(Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(context, id).state)
        }
    }

    @Test fun selectingInstalledModelsPreservesInputOutputVoiceAndRestoresOriginalProfile() {
        val store = AudioEngineProfileStore(context)
        val before = checkNotNull(store.load())
        try {
            ActivityScenario.launch(LanguageModelActivity::class.java).use { scenario ->
                for (entry in LanguageModelCatalog.entries.filter { it.id == DialogueModelId.GEMMA_4_E2B }) {
                    // Owner-facing picker exposes only the protected baseline.
                    if (store.load()?.dialogue == entry.id) continue
                    scenario.onActivity { activity ->
                        val use = button(activity.findViewById(android.R.id.content), "Użyj ${entry.title}")
                        assertTrue(use.isEnabled); use.performClick()
                    }
                    assertEquals(before.copy(dialogue = entry.id), store.load())
                    assertEquals(entry.spec.fileName, Gemma4LiteRtTextBackendFactory.modelFile(context).name)
                    assertEquals(Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(context).state)
                    scenario.recreate()
                    assertEquals(entry.id, checkNotNull(store.load()).dialogue)
                }
            }
        } finally { assertTrue(store.save(before)) }
    }

    @Test fun selectedModelProducesPolishTextAndConstrainedAction() {
        val id = candidate()
        val provider = DialogueEngineProviders.resolve(AudioEngineProfile(dialogue = id))
        assertEquals(id, provider.id)
        assertEquals(Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(context, id).state)
        provider.createDirect(context, "Odpowiadaj po polsku, krótko i rzeczowo. Nie używaj narzędzi.").use { backend ->
            val output = generate(backend, "Jak nazywa się stolica Polski? Odpowiedz jednym słowem.")
            assertTrue("Polish capital answer: $output", output.lowercase().contains("warszaw"))
            assertFalse(output.contains("<think>"))
        }
        provider.createActionClassifier(context,
            "Classify a caller's sentence. For a request to repeat choose ASK_REPEAT. " +
                "For thanks choose ACKNOWLEDGE_NEUTRAL. Return the required JSON only.",
            setOf(DialogueActionId.ASK_REPEAT, DialogueActionId.ACKNOWLEDGE_NEUTRAL)).use { backend ->
            val output = JSONObject(generate(backend, "Proszę powtórzyć, nie usłyszałem."))
            assertEquals("ASK_REPEAT", output.getString("action"))
            assertTrue(output.getDouble("confidence") in 0.0..1.0)
        }
        println("MODEL_NATIVE_PASS=${id.name};RUNTIME=${LanguageModelCatalog.RUNTIME_VERSION}")
    }

    @Test fun selectedModelProducesConstrainedAction() {
        val id = candidate()
        val provider = DialogueEngineProviders.resolve(AudioEngineProfile(dialogue = id))
        provider.createActionClassifier(context,
            "Classify a caller's sentence. For a request to repeat choose ASK_REPEAT. " +
                "For thanks choose ACKNOWLEDGE_NEUTRAL. Return the required JSON only.",
            setOf(DialogueActionId.ASK_REPEAT, DialogueActionId.ACKNOWLEDGE_NEUTRAL)).use { backend ->
            val output = JSONObject(generate(backend, "Proszę powtórzyć, nie usłyszałem."))
            assertEquals("ASK_REPEAT", output.getString("action"))
            assertTrue(output.getDouble("confidence") in 0.0..1.0)
        }
    }

    private fun generate(backend: TextCallAgentBackend, prompt: String): String {
        val latch = CountDownLatch(1)
        val response = AtomicReference<String>()
        val error = AtomicReference<String>()
        val started = System.nanoTime()
        backend.generate(prompt, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { response.set(text); latch.countDown() }
            override fun onError(reason: String) { error.set(reason); latch.countDown() }
        })
        assertTrue("Native model timed out", latch.await(240, TimeUnit.SECONDS))
        assertNull("Native runtime error", error.get())
        println("MODEL_NATIVE_MS=" + (System.nanoTime() - started) / 1_000_000)
        return checkNotNull(response.get())
    }
    private fun awaitTransfer(id: DialogueModelId) {
        val limit = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
        do {
            Thread.sleep(200)
            val state = LanguageModelTransferService.status
            if (state.id == id && !state.busy) return
        } while (System.nanoTime() < limit)
        fail("Model transfer timed out")
    }
    private fun button(view: View, title: String): Button {
        fun search(current: View): Button? {
            if (current is Button && current.text.toString() == title) return current
            if (current is ViewGroup) for (i in 0 until current.childCount) search(current.getChildAt(i))?.let { return it }
            return null
        }
        return checkNotNull(search(view)) { "Missing catalog action: $title" }
    }
}
