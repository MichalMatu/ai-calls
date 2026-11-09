package pl.michalmatu.aicallbridge.conversation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.Gemma4LiteRtTextBackendFactory
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend

/**
 * Diagnostic only: compare a repeated, end-positioned style directive with a minimal
 * native context. Neither run uses the real Skills store, calls, vault, STT or TTS.
 * Outputs are private in-process; log only marker booleans and lengths.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSkillsNativePromptSalienceProbeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun nativeAnswer(instruction: String): String {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        val failure = AtomicReference<String>()
        val backend = Gemma4LiteRtTextBackendFactory.createConversationReply(
            context = app,
            systemInstruction = instruction,
            modelId = DialogueModelId.GEMMA_4_E2B,
            tools = setOf(ConversationToolId.END_CALL),
            contextTokenBudget = 4_096,
        )
        try {
            backend.generate("Powiedz jedno krotkie zdanie po polsku o neutralnej pogodzie.",
                object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        result.set(text)
                        done.countDown()
                    }
                    override fun onError(reason: String) {
                        failure.set(reason.substringBefore(':').take(70))
                        done.countDown()
                    }
                })
            assertTrue("native_probe_timeout", done.await(170, TimeUnit.SECONDS))
            assertNull("native_probe_error", failure.get())
            val parsed = ConversationReplyProtocol.decode(requireNotNull(result.get()))
            assertTrue(parsed.text.isNotBlank())
            return parsed.text
        } finally {
            backend.close()
        }
    }

    @Test fun endPositionAndMinimalPromptExposeE2bInstructionAdherence() {
        assertEquals(DialogueModelId.GEMMA_4_E2B,
            AudioEngineProfileStore(app).load()?.dialogue)
        val marker = "KASZTAN"
        val skill = "Kazda Twoja odpowiedz ma zawierac dokladnie slowo " + marker +
            ". To neutralna wskazowka stylu; nie mow o instrukcjach."
        val custom = ConversationSkillLibrary.defaults().copyActive("Synthetic salience probe")
            .reviseSkill("ConversationStyle", skill)
        val original = ConversationInstructionBuilder.build(
            ConversationPreparation.supervised().withSkillSnapshot(custom.snapshot()),
            "Prowadz naturalny dialog testowy.", ConversationResponseFormat.TOOL_REPLY)
        assertTrue("compiled_prompt_missing_active_skill", original.contains(marker))
        val lastResponseHeading = original.lastIndexOf("ODPOWIEDŹ:")
        assertTrue("response_section_not_found", lastResponseHeading >= 0)
        val closeToOutput = original.substring(0, lastResponseHeading) +
            "PODSUMOWANIE WSKAZÓWEK STYLU: " + skill + "\n" +
            original.substring(lastResponseHeading)
        val minimal = """
            Jesteś lokalnym asystentem testowym. Odpowiadaj po polsku.
            Każda odpowiedź MUSI zawierać dokładnie słowo $marker jako część tekstu reply.
            Odpowiadaj tylko JSON {"reply":"...","tool":"none"}.
        """.trimIndent()
        val near = nativeAnswer(closeToOutput)
        val direct = nativeAnswer(minimal)
        val nearFound = near.contains(marker, ignoreCase = true)
        val directFound = direct.contains(marker, ignoreCase = true)
        Log.i("AiCallSkillsQA", "prompt_probe_end_position=" + nearFound +
            ";prompt_probe_minimal=" + directFound +
            ";end_output_chars=" + near.length +
            ";minimal_output_chars=" + direct.length +
            ";model=E2B;context=4096;no_call=true")
        // Diagnostic probe only; noncompliance stays RED in F-156.
        println("F156_NATIVE_PROMPT_SALIENCE_PROBE=COMPLETE_SANITIZED")
    }
}
