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
import pl.michalmatu.aicallbridge.audioengines.DialogueEngineProviders
import pl.michalmatu.aicallbridge.audioengines.GemmaAudioDirectInputProvider
import pl.michalmatu.aicallbridge.audioengines.ParakeetSpeechInputProvider
import pl.michalmatu.aicallbridge.audioengines.PiperServiceContract
import pl.michalmatu.aicallbridge.audioengines.PiperAndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId
import pl.michalmatu.aicallbridge.audioengines.SpeechOutputId
import pl.michalmatu.aicallbridge.conversation.AndroidSkillLabSessionFactory
import pl.michalmatu.aicallbridge.conversation.ConversationReplyBackend
import pl.michalmatu.aicallbridge.conversation.ConversationInstructionBuilder
import pl.michalmatu.aicallbridge.conversation.ConversationPreparation
import pl.michalmatu.aicallbridge.conversation.ConversationResponseFormat
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
        val outputPcm: ByteArray,
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
        return Turn(user, answer, pcm.size, pcm, stt, approval, output)
    }

    @Test fun realPcmThreeTurnsApplyMessageAndClarificationSkillsAndRecallCorrection() =
        runThreeTurnFlow(null)

    @Test fun matrixFullTurnAndroidGoogle() = runThreeTurnFlow("android-google")
    @Test fun matrixFullTurnAndroidPiper() = runThreeTurnFlow("android-piper")
    @Test fun matrixFullTurnParakeetGoogle() = runThreeTurnFlow("parakeet-google")
    @Test fun matrixFullTurnParakeetPiper() = runThreeTurnFlow("parakeet-piper")
    @Test fun matrixFullTurnGemmaGoogle() = runThreeTurnFlow("gemma-google")
    @Test fun matrixFullTurnGemmaPiper() = runThreeTurnFlow("gemma-piper")

    private fun runThreeTurnFlow(route: String?) {
        val original = checkNotNull(AudioEngineProfileStore(context).load())
        val selected = when (route) {
            "android-google" -> original.copy(
                input = SpeechInputId.ANDROID_STT, output = SpeechOutputId.ANDROID_TTS_PL,
                ttsEnginePackage = "com.google.android.tts", voiceId = null)
            "android-piper" -> original.copy(
                input = SpeechInputId.ANDROID_STT, output = SpeechOutputId.ANDROID_TTS_PL,
                ttsEnginePackage = PiperServiceContract.PACKAGE,
                voiceId = "pl_PL-gosia-medium")
            "parakeet-google" -> original.copy(
                input = SpeechInputId.PARAKEET_PL, output = SpeechOutputId.ANDROID_TTS_PL,
                ttsEnginePackage = "com.google.android.tts", voiceId = null)
            "parakeet-piper" -> original.copy(
                input = SpeechInputId.PARAKEET_PL, output = SpeechOutputId.ANDROID_TTS_PL,
                ttsEnginePackage = PiperServiceContract.PACKAGE,
                voiceId = "pl_PL-gosia-medium")
            "gemma-google" -> original.copy(
                input = SpeechInputId.GEMMA_AUDIO_DIRECT, output = SpeechOutputId.ANDROID_TTS_PL,
                ttsEnginePackage = "com.google.android.tts", voiceId = null)
            "gemma-piper" -> original.copy(
                input = SpeechInputId.GEMMA_AUDIO_DIRECT, output = SpeechOutputId.ANDROID_TTS_PL,
                ttsEnginePackage = PiperServiceContract.PACKAGE,
                voiceId = "pl_PL-gosia-medium")
            null -> original
            else -> error("unrecognized_offcall_matrix_route")
        }
        assertEquals("g52_gemma_e2b_required", DialogueModelId.GEMMA_4_E2B, original.dialogue)
        assertNull("g52_invalid_profile", AudioEngineRegistry.incompatibility(selected))
        val routing = Collections.synchronizedList(mutableListOf<SkillTurnRoutingDecision>())
        val factory = AndroidSkillLabSessionFactory(context, contextTokenBudget = 4096)
        // G5.3 opt-in comparison: default remains identical to G5.2 CONTEXTUAL.
        val mode = when (InstrumentationRegistry.getArguments().getString("g53_skills_mode")) {
            null, "", "CONTEXTUAL" -> SkillLoadingMode.CONTEXTUAL
            "ALL_ACTIVE" -> SkillLoadingMode.ALL_ACTIVE
            else -> error("g53_unknown_skills_mode")
        }
        factory.skillLoadingMode = mode
        factory.onTurnRouting = { routing.add(it) }
        // For native audio, use the actual production model provider; the old
        // text-only Lab wrapper cannot forward generateAudio. Never save selected.
        val directAudio = selected.input == SpeechInputId.GEMMA_AUDIO_DIRECT
        val raw = if (directAudio) DialogueEngineProviders.resolve(selected)
            .createConversationReply(
                context,
                ConversationInstructionBuilder.build(
                    ConversationPreparation.supervised(),
                    "Prowadź rozmowę o terminie spotkania. Zapamiętaj ostatnią korektę i odpowiedz na pytanie.",
                    ConversationResponseFormat.TOOL_REPLY),
                setOf(ConversationToolId.END_CALL))
        else factory.open("natural").createStoryBackend()
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
        val input = when (selected.input) {
            SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(context, "pl-PL")
            SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(context)
            SpeechInputId.GEMMA_AUDIO_DIRECT -> GemmaAudioDirectInputProvider()
        }
        val output = if (selected.effectiveTtsEnginePackage == PiperServiceContract.PACKAGE)
            PiperAndroidTtsOutputProvider(context, "pl-PL", selected.voiceId)
        else
            AndroidTtsOutputProvider(context, "pl-PL", selected.voiceId,
                selected.effectiveTtsEnginePackage)
        val pipeline = LocalSpeechTextPipeline(
            context, model,
            TextOutputApprovalPolicy { candidate ->
                if (candidate.isNotBlank()) TextOutputDecision.RELEASE else TextOutputDecision.DROP
            },
            input = input, output = output,
            languageTag = "pl-PL", voiceId = selected.voiceId)
        val expected = listOf(
            "Proszę zapisać wiadomość o spotkaniu w poniedziałek.",
            "Sprostowanie wiadomości. Spotkanie będzie we wtorek, nie w poniedziałek.",
            "Przypomnij, w jaki dzień jest spotkanie z poprzedniej wiadomości."
        )
        val turns = mutableListOf<Turn>()
        // Opt-in only. Normal G5.2 regression creates no persistent audio or transcript.
        val capture = InstrumentationRegistry.getArguments().getString("g53_private_evidence") == "true"
        val evidence = if (capture) mutableListOf<G53CapturedAudioTurn>() else null
        try {
            for (phrase in expected) {
                val spoken = synthesizeSyntheticPcm(phrase)
                val observed = runAudioTurn(pipeline, spoken)
                turns.add(observed)
                evidence?.add(G53CapturedAudioTurn(
                    phrase, observed.transcript, observed.approved,
                    spoken, observed.outputPcm, observed.sttMs, observed.approvalMs,
                    observed.outputMs,
                ))
            }
            assertEquals(3, turns.size)
            assertTrue("g52_first_stt_missing_message", turns[0].transcript.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("wiadomo"))
            assertTrue("g52_correction_stt_missing", turns[1].transcript.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("sprostowan"))
            assertTrue("g52_correction_day_stt_missing", turns[1].transcript.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("wtor"))
            assertTrue("g52_memory_final_day", turns[2].approved.lowercase(
                Locale.forLanguageTag("pl-PL")).contains("wtor"))
            val decisions = routing.toList()
            if (directAudio) {
                // Native audio uses one shared staged Engine, not Lab skill routing.
                assertTrue("g52_audio_direct_no_lab_router", decisions.isEmpty())
            } else if (mode == SkillLoadingMode.CONTEXTUAL) {
                assertEquals("g52_three_routing_events", listOf(1,2,3),
                    decisions.map { it.turn })
                assertTrue("g52_message_skill_not_activated",
                    decisions[0].activatedIds.contains("MessageCapture"))
                assertTrue("g52_clarification_skill_not_activated",
                    decisions[1].activatedIds.contains("Clarification"))
                assertTrue("g52_third_turn_unexpected_refresh",
                    decisions[2].activatedIds.isEmpty() &&
                        decisions[2].deactivatedIds.isEmpty())
            } else {
                // ALL_ACTIVE does not create the contextual routing wrapper.
                assertTrue("g53_all_active_must_not_claim_dynamic_routing",
                    decisions.isEmpty())
            }
            assertFalse("g52_tools_must_never_execute", effects.get())
            assertEquals(3, decodedTools.size)
            turns.forEach { assertTrue(it.approved.isNotBlank()) }
            if (evidence != null) {
                G53PrivateAudioEvidence.save(
                    context, original.dialogue.name, original.input.name,
                    original.output.name, evidence, decisions, mode,
                )
                // Never put even synthetic utterances, replies, WAV, or private paths in logcat.
                Log.i("AiCallG53Evidence",
                    "complete=true;skills_mode=" + mode.name + ";turns=" + evidence.size +
                    ";wav_files=" + evidence.size * 2 +
                    ";on_device_private=true;no_call=true")
            }
            // Aggregated, synthetic-only numeric timing markers for owner diagnostics.
            val metricPrefix = when (route) {
                "android-google" -> "FULL_ANDROID_GOOGLE"
                "android-piper" -> "FULL_ANDROID_PIPER"
                "parakeet-google" -> "FULL_PARAKEET_GOOGLE"
                "parakeet-piper" -> "FULL_PARAKEET_PIPER"
                "gemma-google" -> "FULL_GEMMA_GOOGLE"
                "gemma-piper" -> "FULL_GEMMA_PIPER"
                else -> "FULL_SELECTED"
            }
            turns.forEachIndexed { index, turn ->
                val stub = "AUDIO_QA_" + metricPrefix + "_CASE_" + (index + 1) + "_"
                InstrumentationRegistry.getInstrumentation().sendStatus(
                    2, android.os.Bundle().apply {
                        // Frame every line so instrumentation renders first/last
                        // numeric metrics with the same prefix as middle lines.
                        putString("stream", "\n" + stub + "STT_MS=" + turn.sttMs + "\n" +
                            stub + "APPROVAL_MS=" + turn.approvalMs + "\n" +
                            stub + "OUTPUT_MS=" + turn.outputMs + "\n" +
                            stub + "PASSED=1\n")
                    })
            }
            Log.i("AiCallG52E2E", "model=E2B;offcall=true;profile_input=" +
                selected.input.name + ";profile_output=" + selected.output.name +
                ";audio_turns=3;route_events=" + decisions.size +
                ";skills_mode=" + mode.name + ";memory_day_correct=true;" +
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
