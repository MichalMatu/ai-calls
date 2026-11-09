package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AndroidSttInputProvider
import pl.michalmatu.aicallbridge.audioengines.AndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.audioengines.ParakeetSpeechInputProvider
import pl.michalmatu.aicallbridge.audioengines.PiperAndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId
import pl.michalmatu.aicallbridge.audioengines.SpeechOutputId
import pl.michalmatu.aicallbridge.conversation.AndroidSkillLabSessionFactory
import pl.michalmatu.aicallbridge.conversation.ConversationReplyBackend
import pl.michalmatu.aicallbridge.conversation.LabToolProposalPolicy
import pl.michalmatu.aicallbridge.conversation.SkillLoadingMode
import pl.michalmatu.aicallbridge.conversation.SkillTurnRoutingDecision
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextOutputDecision

/** G5.2 hardware: three real audio turns, one Gemma E2B and one skill-pinned dialogue. */
@RunWith(AndroidJUnit4::class)
class AndroidG52MultiturnSpeechSkillsE2ETest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun synthesizeSyntheticPcm(sentence: String): ByteArray {
        val pcm = AtomicReference<ByteArray?>()
        val failed = AtomicReference<String?>()
        val ready = CountDownLatch(1)
        val tts = LocalTtsSpeechOutput(context, "pl-PL")
        try {
            instrumentation.runOnMainSync {
                tts.synthesize(sentence, object : LocalTtsSpeechOutput.Listener {
                    override fun onPcm16Mono16k(bytes: ByteArray) {
                        pcm.set(bytes)
                        ready.countDown()
                    }
                    override fun onError(reason: String) {
                        failed.set(reason)
                        ready.countDown()
                    }
                })
            }
            assertTrue("g52_source_tts_timeout", ready.await(120, TimeUnit.SECONDS))
            assertNull("g52_source_tts_error", failed.get())
            val data = checkNotNull(pcm.get()) { "g52_source_pcm_absent" }
            assertTrue("g52_source_pcm_short", data.size > 8_000)
            assertEquals(0, data.size % 2)
            return data
        } finally {
            instrumentation.runOnMainSync { tts.close() }
        }
    }

    private data class Turn(
        val transcript: String,
        val approved: String,
        val pcmBytes: Int,
        val sttMs: Long,
        val approvalMs: Long,
        val outputMs: Long,
    )

    private fun runAudioTurn(pipeline: LocalSpeechTextPipeline, inputPcm: ByteArray): Turn {
        val done = CountDownLatch(1)
        val transcript = AtomicReference<String?>()
        val approved = AtomicReference<String?>()
        val result = AtomicReference<ByteArray?>()
        val error = AtomicReference<String?>()
        val writerStarted = AtomicBoolean(false)
        val startedAt = System.nanoTime()
        val sttMs = AtomicReference<Long?>()
        val approvalMs = AtomicReference<Long?>()
        val outputMs = AtomicReference<Long?>()
        instrumentation.runOnMainSync {
            pipeline.start(object : LocalSpeechTextPipeline.Listener {
                override fun onSpeechInputReady() {
                    if (!writerStarted.compareAndSet(false, true)) return
                    Thread({
                        try {
                            val pad = ByteArray(LocalSpeechFormat.bytesForDurationMs(420))
                            val chunk = LocalSpeechFormat.bytesForDurationMs(20)
                            fun writePaced(data: ByteArray) {
                                var offset = 0
                                while (offset < data.size) {
                                    val n = minOf(chunk, data.size - offset)
                                    check(pipeline.writeInputPcm(data, offset, n)) {
                                        "g52_audio_pcm_stream_rejected"
                                    }
                                    offset += n
                                    Thread.sleep(20)
                                }
                            }
                            writePaced(pad)
                            writePaced(inputPcm)
                            writePaced(pad)
                            pipeline.finishInput()
                        } catch (e: Throwable) {
                            error.set("g52_stream_" + e.javaClass.simpleName)
                            done.countDown()
                        }
                    }, "G52SyntheticAudioInput").start()
                }
                override fun onUserTranscript(text: String) {
                    transcript.set(text)
                    sttMs.set((System.nanoTime() - startedAt) / 1_000_000L)
                }
                override fun onApprovedText(text: String) {
                    approved.set(text)
                    approvalMs.set((System.nanoTime() - startedAt) / 1_000_000L)
                }
                override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                    result.set(pcm)
                    outputMs.set((System.nanoTime() - startedAt) / 1_000_000L)
                    done.countDown()
                }
                override fun onDroppedText() {
                    error.set("g52_output_dropped")
                    done.countDown()
                }
                override fun onError(reason: String) {
                    error.set("g52_pipeline_" + reason)
                    done.countDown()
                }
            })
        }
        assertTrue("g52_turn_timed_out", done.await(360, TimeUnit.SECONDS))
        assertNull("g52_turn_error", error.get())
        val user = checkNotNull(transcript.get()) { "g52_transcript_missing" }
        val answer = checkNotNull(approved.get()) { "g52_approved_reply_missing" }
        val pcm = checkNotNull(result.get()) { "g52_output_pcm_missing" }
        assertTrue("g52_output_short", pcm.size > 3200)
        assertEquals("g52_pcm_alignment", 0, pcm.size % 2)
        val stt = checkNotNull(sttMs.get())
        val approval = checkNotNull(approvalMs.get())
        val output = checkNotNull(outputMs.get())
        assertTrue("g52_stt_before_approval", stt <= approval)
        assertTrue("g52_approval_before_tts", approval <= output)
        return Turn(user, answer, pcm.size, stt, approval, output)
    }

    @Test fun realPcmThreeTurnsApplyMessageAndClarificationSkillsAndRecallCorrection() {
        val original = checkNotNull(AudioEngineProfileStore(context).load())
        assertEquals("g52_gemma_e2b_required", DialogueModelId.GEMMA_4_E2B, original.dialogue)
        assertNull("g52_invalid_profile", AudioEngineRegistry.incompatibility(original))
        val routing = Collections.synchronizedList(mutableListOf<SkillTurnRoutingDecision>())
        val factory = AndroidSkillLabSessionFactory(context, contextTokenBudget = 4096)
        factory.skillLoadingMode = SkillLoadingMode.CONTEXTUAL
        factory.onTurnRouting = { routing.add(it) }
        val raw = factory.open("natural").createStoryBackend()
        val effects = AtomicBoolean(false)
        val decodedTools = Collections.synchronizedList(mutableListOf<ConversationToolId?>())
        val model = ConversationReplyBackend(raw) { reply, _, invalid ->
            if (!invalid && reply != null) {
                val verdict = LabToolProposalPolicy.validate(
                    reply, setOf(ConversationToolId.END_CALL))
                effects.set(effects.get() || verdict.executed)
                decodedTools.add(verdict.acceptedProposal)
            }
        }
        val input = when (original.input) {
            SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(context, "pl-PL")
            SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(context)
            else -> error("g52_input_unsupported")
        }
        val output = when (original.output) {
            SpeechOutputId.ANDROID_TTS_PL ->
                AndroidTtsOutputProvider(context, "pl-PL", original.voiceId)
            SpeechOutputId.PIPER_PL ->
                PiperAndroidTtsOutputProvider(context, "pl-PL", original.voiceId)
            else -> error("g52_output_unsupported")
        }
        val pipeline = LocalSpeechTextPipeline(
            context, model,
            TextOutputApprovalPolicy { candidate ->
                if (candidate.isNotBlank()) TextOutputDecision.RELEASE else TextOutputDecision.DROP
            },
            input = input, output = output,
            languageTag = "pl-PL", voiceId = original.voiceId)
        val expected = listOf(
            "Proszę zapisać wiadomość o spotkaniu w poniedziałek.",
            "Sprostowanie wiadomości. Spotkanie będzie we wtorek, nie w poniedziałek.",
            "Przypomnij, w jaki dzień jest spotkanie z poprzedniej wiadomości."
        )
        val turns = mutableListOf<Turn>()
        try {
            for (phrase in expected) {
                turns.add(runAudioTurn(pipeline, synthesizeSyntheticPcm(phrase)))
            }
            assertEquals(3, turns.size)
            assertTrue("g52_first_stt_missing_message", turns[0].transcript.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("wiadomo"))
            assertTrue("g52_correction_stt_missing", turns[1].transcript.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("sprostowan"))
            assertTrue("g52_correction_day_stt_missing", turns[1].transcript.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("wtork"))
            assertTrue("g52_memory_final_day", turns[2].approved.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("wtork"))
            val decisions = routing.toList()
            assertEquals("g52_three_routing_events", listOf(1,2,3),
                decisions.map { it.turn })
            assertTrue("g52_message_skill_not_activated",
                decisions[0].activatedIds.contains("MessageCapture"))
            assertTrue("g52_clarification_skill_not_activated",
                decisions[1].activatedIds.contains("Clarification"))
            assertTrue("g52_third_turn_unexpected_refresh",
                decisions[2].activatedIds.isEmpty() &&
                    decisions[2].deactivatedIds.isEmpty())
            assertFalse("g52_tools_must_never_execute", effects.get())
            assertEquals(3, decodedTools.size)
            turns.forEach { assertTrue(it.approved.isNotBlank()) }
            Log.i("AiCallG52E2E", "model=E2B;offcall=true;profile_input=" +
                original.input.name + ";profile_output=" + original.output.name +
                ";audio_turns=3;route_events=" + decisions.size +
                ";skills_activated=MessageCapture,Clarification;memory_day_correct=true;" +
                "pcm_bytes_total=" + turns.sumOf { it.pcmBytes } +
                ";stt_ms=" + turns.joinToString(",") { it.sttMs.toString() } +
                ";approve_ms=" + turns.joinToString(",") { it.approvalMs.toString() } +
                ";tts_end_ms=" + turns.joinToString(",") { it.outputMs.toString() } +
                ";tool_executed=false;stop=true;no_call=true")
        } finally {
            pipeline.cancel()
            pipeline.close()
        }
        assertEquals("g52_profile_preserved", original, AudioEngineProfileStore(context).load())
    }
}
