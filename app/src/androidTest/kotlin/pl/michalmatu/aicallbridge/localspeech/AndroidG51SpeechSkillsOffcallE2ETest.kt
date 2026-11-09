package pl.michalmatu.aicallbridge.localspeech

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
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

/**
 * G5.1 actual off-call audio path:
 * local TTS synthetic source → selected on-device STT → E2B/GPU +
 * version-pinned per-turn Skills → app text approval → selected offline TTS.
 * No mic, speaker playback, Telecom, Call, real customer data or effect.
 * No raw transcripts/audio leave the app in logcat or GitHub receipts.
 */
@RunWith(AndroidJUnit4::class)
class AndroidG51SpeechSkillsOffcallE2ETest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun syntheticPolishPcmPassesSttGemmaSkillsApprovalTtsAndStop() {
        val profile = checkNotNull(AudioEngineProfileStore(context).load())
        assertEquals("g51_e2b_profile_required", DialogueModelId.GEMMA_4_E2B, profile.dialogue)
        assertNull("g51_profile_incompatible", AudioEngineRegistry.incompatibility(profile))
        val routing = Collections.synchronizedList(mutableListOf<SkillTurnRoutingDecision>())
        val sessionFactory = AndroidSkillLabSessionFactory(context, contextTokenBudget = 4_096)
        sessionFactory.skillLoadingMode = SkillLoadingMode.CONTEXTUAL
        sessionFactory.onTurnRouting = { routing.add(it) }

        // Build synthetic, audible-length local PCM; never record the microphone.
        val sourcePcm = AtomicReference<ByteArray?>()
        val sourceError = AtomicReference<String?>()
        val sourceReady = CountDownLatch(1)
        val syntheticSource = LocalTtsSpeechOutput(context, "pl-PL")
        try {
            instrumentation.runOnMainSync {
                syntheticSource.synthesize(
                    "Proszę zapisać krótką wiadomość testową o spotkaniu.",
                    object : LocalTtsSpeechOutput.Listener {
                        override fun onPcm16Mono16k(pcm: ByteArray) {
                            sourcePcm.set(pcm)
                            sourceReady.countDown()
                        }
                        override fun onError(reason: String) {
                            sourceError.set(reason)
                            sourceReady.countDown()
                        }
                    },
                )
            }
            assertTrue("g51_source_tts_timeout", sourceReady.await(120, TimeUnit.SECONDS))
            assertNull("g51_source_tts_error", sourceError.get())
        } finally {
            instrumentation.runOnMainSync { syntheticSource.close() }
        }
        val pcm = checkNotNull(sourcePcm.get())
        assertTrue("g51_source_pcm_missing", pcm.size > 8_000)
        assertEquals("g51_source_pcm_not_aligned", 0, pcm.size % 2)

        val input = when (profile.input) {
            SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(context, "pl-PL")
            SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(context)
            else -> error("g51_unsupported_selected_input")
        }
        val output = when (profile.output) {
            SpeechOutputId.ANDROID_TTS_PL ->
                AndroidTtsOutputProvider(context, "pl-PL", profile.voiceId)
            SpeechOutputId.PIPER_PL ->
                PiperAndroidTtsOutputProvider(context, "pl-PL", profile.voiceId)
            else -> error("g51_unsupported_selected_output")
        }
        val rawModel = sessionFactory.open("natural").createStoryBackend()
        val proposedTool = AtomicReference<ConversationToolId?>()
        val toolExecuted = AtomicBoolean(false)
        val model = ConversationReplyBackend(rawModel) { reply, _, invalid ->
            if (!invalid && reply != null) {
                val assessed = LabToolProposalPolicy.validate(
                    reply, setOf(ConversationToolId.END_CALL))
                proposedTool.set(assessed.acceptedProposal)
                toolExecuted.set(assessed.executed)
            }
        }
        val pipeline = LocalSpeechTextPipeline(
            context, model,
            TextOutputApprovalPolicy { candidate ->
                if (candidate.isNotBlank()) TextOutputDecision.RELEASE else TextOutputDecision.DROP
            },
            languageTag = "pl-PL", voiceId = profile.voiceId,
            input = input, output = output,
        )
        val done = CountDownLatch(1)
        val transcriptSeen = AtomicBoolean(false)
        val responseSeen = AtomicBoolean(false)
        val bytes = AtomicReference<ByteArray?>()
        val error = AtomicReference<String?>()
        val startedAt = System.nanoTime()
        val sttMs = AtomicReference<Long?>()
        val approvedMs = AtomicReference<Long?>()
        val totalMs = AtomicReference<Long?>()
        val writerStarted = AtomicBoolean(false)
        try {
            instrumentation.runOnMainSync {
                pipeline.start(object : LocalSpeechTextPipeline.Listener {
                    override fun onSpeechInputReady() {
                        if (!writerStarted.compareAndSet(false, true)) return
                        Thread({
                            try {
                                val silence = ByteArray(LocalSpeechFormat.bytesForDurationMs(450))
                                val step = LocalSpeechFormat.bytesForDurationMs(20)
                                fun stream(data: ByteArray) {
                                    var offset = 0
                                    while (offset < data.size) {
                                        val size = minOf(step, data.size - offset)
                                        check(pipeline.writeInputPcm(data, offset, size)) {
                                            "g51_input_pcm_write_failed"
                                        }
                                        offset += size
                                        Thread.sleep(20)
                                    }
                                }
                                stream(silence)
                                stream(pcm)
                                stream(silence)
                                pipeline.finishInput()
                            } catch (failed: Throwable) {
                                error.set("g51_stream_" + failed.javaClass.simpleName)
                                done.countDown()
                            }
                        }, "G51SyntheticPcmFeed").start()
                    }
                    override fun onUserTranscript(text: String) {
                        transcriptSeen.set(text.isNotBlank())
                        sttMs.set((System.nanoTime() - startedAt) / 1_000_000)
                    }
                    override fun onApprovedText(text: String) {
                        responseSeen.set(text.isNotBlank())
                        approvedMs.set((System.nanoTime() - startedAt) / 1_000_000)
                    }
                    override fun onOutputPcm16Mono16k(pcm: ByteArray) {
                        bytes.set(pcm)
                        totalMs.set((System.nanoTime() - startedAt) / 1_000_000)
                        done.countDown()
                    }
                    override fun onDroppedText() {
                        error.set("g51_approval_dropped")
                        done.countDown()
                    }
                    override fun onError(reason: String) {
                        error.set("g51_pipeline_" + reason)
                        done.countDown()
                    }
                })
            }
            assertTrue("g51_pipeline_timeout", done.await(420, TimeUnit.SECONDS))
            assertNull("g51_pipeline_error", error.get())
            assertTrue("g51_stt_text_missing", transcriptSeen.get())
            assertTrue("g51_model_approval_missing", responseSeen.get())
            val result = checkNotNull(bytes.get()) { "g51_output_pcm_missing" }
            assertTrue("g51_output_pcm_too_short", result.size > 3_200)
            assertEquals("g51_output_pcm_not_aligned", 0, result.size % 2)
            assertEquals("g51_one_router_event", listOf(1), routing.map { it.turn })
            assertFalse("g51_model_tool_must_never_execute", toolExecuted.get())
            assertTrue("g51_stt_before_approval",
                checkNotNull(sttMs.get()) <= checkNotNull(approvedMs.get()))
            assertTrue("g51_approval_before_tts",
                checkNotNull(approvedMs.get()) <= checkNotNull(totalMs.get()))
            Log.i("AiCallG51E2E",
                "model=E2B;offcall=true;stt=true;skills_turns=" + routing.size +
                ";approved=true;tts_pcm_bytes=" + result.size +
                ";stt_ms=" + sttMs.get() + ";model_approval_ms=" + approvedMs.get() +
                ";tts_finished_ms=" + totalMs.get() +
                ";proposal_present=" + (proposedTool.get() != null) +
                ";tool_executed=false;stop=true")
        } finally {
            pipeline.cancel()
            pipeline.close()
        }
        assertEquals("g51_owner_profile_preserved", profile, AudioEngineProfileStore(context).load())
    }
}
