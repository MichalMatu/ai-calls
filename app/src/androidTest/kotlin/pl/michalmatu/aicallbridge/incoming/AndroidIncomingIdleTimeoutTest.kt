package pl.michalmatu.aicallbridge.incoming

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.*
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.conversationcore.IncomingIdleTimeout
import pl.michalmatu.aicallbridge.conversationtools.*
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline

@RunWith(AndroidJUnit4::class)
class AndroidIncomingIdleTimeoutTest {
    @Test fun selectedAndroidTtsWarnsThenPlaysFarewellBeforeToolWithoutTelecom() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val profile = checkNotNull(AudioEngineProfileStore(context).load())
        assertEquals(SpeechOutputId.ANDROID_TTS_PL, profile.output)
        var toolRequests = 0
        var turn = 1
        val tools = ConversationToolExecutor(object : ConversationToolPort {
            override fun enabled(tool: ConversationToolId) = true
            override fun authorized(tool: ConversationToolId) = true
            override fun requestEndCall(turn: Int): Boolean { toolRequests++; return true }
        })
        val preparation = ConversationPreparation.incoming()
        val configuration = ConversationRunConfiguration.ModelDirect(
            profile, ConversationInstructionBuilder.build(preparation, IncomingSecretaryScenario.GOAL,
                ConversationResponseFormat.TOOL_REPLY), preparation,
        )
        val scenario = IncomingSecretaryScenario(context, configuration, tools, setOf(ConversationToolId.END_CALL))
        val policy = checkNotNull(scenario.idlePolicy)
        val clock = IncomingIdleTimeout(policy.silenceMs, policy.graceMs)
        val session = scenario.open(ConversationRuntimeHooks({ true }, { 5L }, { turn }, {},
            ConversationDecisionSink { _, _, _, _, _ -> }, {}))
        val silentInput = object : SpeechInputProvider {
            override fun start(listener: SpeechInputProvider.Listener) = Unit
            override fun writePcm(bytes: ByteArray, offset: Int, length: Int) = true
            override fun finishInput() = Unit
            override fun cancel() = Unit
            override fun close() = Unit
        }
        val pipeline = LocalSpeechTextPipeline(context, session.backend, session.approvalPolicy,
            languageTag = profile.languageTag, voiceId = profile.voiceId,
            input = silentInput, output = AndroidTtsOutputProvider(context, profile.languageTag, profile.voiceId))
        fun say(text: String) {
            val delivered = AtomicReference<String>()
            val pcmSize = AtomicReference<Int>()
            val error = AtomicReference<String>()
            val done = CountDownLatch(1)
            instrumentation.runOnMainSync {
                pipeline.speakCandidate(text, object : LocalSpeechTextPipeline.Listener {
                    override fun onSpeechInputReady() = Unit
                    override fun onUserTranscript(text: String) = Unit
                    override fun onApprovedText(text: String) { delivered.set(text) }
                    override fun onOutputPcm16Mono16k(pcm: ByteArray) { pcmSize.set(pcm.size); done.countDown() }
                    override fun onDroppedText() { error.set("dropped"); done.countDown() }
                    override fun onError(reason: String) { error.set(reason); done.countDown() }
                })
            }
            assertTrue("TTS_TIMEOUT", done.await(45, TimeUnit.SECONDS))
            assertNull(error.get())
            assertEquals(text, delivered.get())
            assertTrue((pcmSize.get() ?: 0) > 320)
        }
        try {
            session.beginTurn()
            assertEquals(30_000L, clock.remainingMs(1_000L))
            assertEquals(IncomingIdleTimeout.Action.WARN, clock.expire(31_000L))
            say(policy.warningText)
            instrumentation.runOnMainSync {
                assertNull(session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
                assertEquals(0, toolRequests)
            }
            turn = 2
            session.beginTurn()
            assertEquals(15_000L, clock.remainingMs(100_000L))
            assertEquals(IncomingIdleTimeout.Action.CLOSE, clock.expire(115_000L))
            assertTrue(session.armIdleClosure())
            say(policy.farewellText)
            instrumentation.runOnMainSync {
                assertEquals(0, toolRequests)
                assertEquals(ConversationOutcome.Completed, session.takeOutcome(ConversationOutcomeBoundary.PLAYBACK_COMPLETE))
                assertEquals(1, toolRequests)
            }
        } finally {
            instrumentation.runOnMainSync { pipeline.close(); session.close() }
        }
    }
}
