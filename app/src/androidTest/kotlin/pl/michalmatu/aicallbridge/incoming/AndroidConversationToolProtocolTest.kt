package pl.michalmatu.aicallbridge.incoming

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.*
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.textagent.*

@RunWith(AndroidJUnit4::class)
class AndroidConversationToolProtocolTest {
    @Test fun selectedModelSeparatesCorrectionAndQuotedFarewellFromRealEndRequest() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val profile = checkNotNull(AudioEngineProfileStore(context).load())
        val preparation = ConversationPreparation.incoming()
        val config = ConversationRunConfiguration.ModelDirect(profile,
            ConversationInstructionBuilder.build(preparation, IncomingSecretaryScenario.GOAL, ConversationResponseFormat.TOOL_REPLY), preparation)
        val stories = listOf(listOf(
            "Dzień dobry, chcę zostawić wiadomość z serwisu rowerowego. Kosztorys jest gotowy, proszę o kontakt jutro po szesnastej.",
            "W treści wiadomości są słowa do widzenia, ale nie kończę naszej rozmowy. Mam jeszcze poprawkę.",
            "Proszę zmienić termin na piątek po osiemnastej zamiast jutro. To jeszcze nie koniec, zaraz poproszę o podsumowanie.",
            "Proszę podsumować wiadomość i podać poprawiony termin.",
            "Tak, zgadza się. To wszystko, nie mam więcej spraw. Dziękuję, do widzenia.",
        ), listOf("Proszę teraz zakończyć to połączenie i rozłączyć się."))
        val evidence = JSONArray()
        for (story in stories) {
            var turn = 0
            var requests = 0
            val events = mutableListOf<ConversationToolEvent>()
            val tools = ConversationToolExecutor(object : ConversationToolPort {
                override fun enabled(tool: ConversationToolId) = true
                override fun authorized(tool: ConversationToolId) = true
                override fun requestEndCall(turn: Int): Boolean { requests++; return true } // No Telecom.
            })
            lateinit var session: ConversationDialogueSession
            instrumentation.runOnMainSync {
                session = IncomingSecretaryScenario(context, config, tools, setOf(ConversationToolId.END_CALL)).open(
                    ConversationRuntimeHooks({ true }, { 1L }, { turn }, {}, ConversationDecisionSink { _,_,_,_,_ -> }, events::add))
                session.start()
            }
            val controller = TextCallTurnController(session.backend, session.approvalPolicy)
            try {
                story.forEachIndexed { index, input ->
                    turn = index + 1
                    val output = AtomicReference<String>()
                    val error = AtomicReference<String>()
                    val done = CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        session.beginTurn()
                        controller.submitUserText(input, object : TextCallTurnController.Listener {
                            override fun onApprovedResponse(text: String) { output.set(text); done.countDown() }
                            override fun onDroppedResponse() { error.set("model_output_dropped"); done.countDown() }
                            override fun onError(reason: String) { error.set(reason); done.countDown() }
                        })
                    }
                    assertTrue("native_tool_reply_timeout", done.await(120, TimeUnit.SECONDS))
                    assertNull("native_tool_reply_error", error.get())
                    val speech = checkNotNull(output.get())
                    assertTrue(speech.isNotBlank())
                    assertFalse(speech.trimStart().startsWith("{"))
                    instrumentation.runOnMainSync {
                        // Controller already approved the speech and cancelled its backend.
                        assertNull(session.takeOutcome(ConversationOutcomeBoundary.TRANSCRIPT))
                        assertEquals(0, requests)
                        val outcome = session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE)
                        if (index == story.lastIndex) {
                            assertEquals(ConversationOutcome.Completed, outcome)
                            assertEquals(1, requests)
                        } else { assertNull("premature_end_call", outcome); assertEquals(0, requests) }
                    }
                    evidence.put(JSONObject().put("story_size", story.size).put("turn", turn)
                        .put("input", input).put("reply", speech).put("end_requested", requests == 1))
                }
            } finally { instrumentation.runOnMainSync { controller.close(); session.close() } }
            assertEquals(ConversationToolStage.DISPATCHED, events.last().stage)
            assertFalse(events.any { it.stage == ConversationToolStage.CONFIRMED })
        }
        val file = File(context.cacheDir, "conversation-tool-protocol-${UUID.randomUUID()}.json")
        file.writeText(JSONObject().put("actual_call", false).put("actual_stt", false).put("actual_tts", false)
            .put("actual_model", true).put("dialogue", profile.dialogue.name).put("turns", evidence).toString())
    }
}
