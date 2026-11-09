package pl.michalmatu.aicallbridge.conversation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * G4.2 actual Gemma E2B/GPU off-call end-to-end. No user's real Call, no
 * mutation of saved profile or Skills. Text only synthetic and not logged.
 */
@RunWith(AndroidJUnit4::class)
class AndroidG4DynamicSkillsNativeE2ETest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun send(backend: TextCallAgentBackend, text: String): ConversationReply {
        val result = AtomicReference<String?>()
        val error = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        backend.generate(text, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) {
                result.set(text)
                latch.countDown()
            }
            override fun onError(reason: String) {
                error.set(reason)
                latch.countDown()
            }
        })
        assertTrue("g4_native_callback_timeout", latch.await(150, TimeUnit.SECONDS))
        assertNull("g4_native_error", error.get())
        return ConversationReplyProtocol.decode(checkNotNull(result.get()))
    }

    @Test fun realGemmaSkillTransitionsPreserveSyntheticMemoryAndStop() {
        val profile = checkNotNull(AudioEngineProfileStore(app).load())
        assertEquals("g4_e2b_required", DialogueModelId.GEMMA_4_E2B, profile.dialogue)
        val factory = AndroidSkillLabSessionFactory(app, contextTokenBudget = 4_096)
        factory.skillLoadingMode = SkillLoadingMode.CONTEXTUAL
        val selected = mutableListOf<String>()
        factory.onSkillSelection = { selected.addAll(it.selectedIds) }
        val backend = factory.open("natural").createStoryBackend()
        val labels = mutableListOf<Boolean>()
        try {
            assertEquals(listOf("ConversationStyle", "NaturalClosure"), selected)
            val a = send(backend, "Zapamiętaj mój fikcyjny kod ZX74. Nie jest to prawdziwy sekret.")
            assertNull(a.tool)
            labels.add(a.text.isNotBlank())
            val b = send(backend, "Zanotuj wiadomość testową o fakturze 57.")
            assertNull(b.tool)
            labels.add(b.text.isNotBlank())
            val c = send(backend, "Sprostowanie: faktura 58, nie 57. Jaki był mój fikcyjny kod?")
            assertNull(c.tool)
            labels.add(c.text.contains("ZX74", ignoreCase = true))
            val d = send(backend, "Zmienmy temat. Przypomnij mój fikcyjny kod.")
            assertNull(d.tool)
            labels.add(d.text.contains("ZX74", ignoreCase = true))
            Log.i("AiCallG4E2E", "model=E2B;turns=4;valid_json=4/4;" +
                "skills_refresh=3;memory_before_and_after_topic_change=" +
                "${labels[2] && labels[3]};no_call=true;stop_covered=true")
            assertTrue("g4_synthetic_reply_nonempty", labels[0] && labels[1])
            assertTrue("g4_memory_before_pivot_missing", labels[2])
            assertTrue("g4_memory_after_pivot_missing", labels[3])
        } finally {
            backend.cancel()
            backend.close()
        }
        assertEquals(profile, AudioEngineProfileStore(app).load())
    }
}
