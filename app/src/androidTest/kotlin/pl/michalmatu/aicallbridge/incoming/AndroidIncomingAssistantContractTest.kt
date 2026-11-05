package pl.michalmatu.aicallbridge.incoming

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import android.content.ContextWrapper
import android.media.AudioManager
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.*
import pl.michalmatu.aicallbridge.campaign.CampaignAuthorizationStore
import pl.michalmatu.aicallbridge.conversation.*
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.*

@RunWith(AndroidJUnit4::class)
class AndroidIncomingAssistantContractTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext

    @Test fun acceptedReceivingPolicyPersistsSeparatelyAndCanBeRevokedWithoutOutgoingChanges() {
        val name="incoming-test-${UUID.randomUUID()}"
        val outgoing=CampaignAuthorizationStore(context).load()
        val profile=AudioEngineProfileStore(context).load()
        try {
            val first=IncomingAssistantPolicyStore(context,name)
            assertEquals(AnsweringMode.MANUAL,first.load().mode)
            val saved=first.save(AnsweringMode.AI,"synthetic-account",true)
            val reopened=IncomingAssistantPolicyStore(context,name)
            assertEquals(saved,reopened.load())
            reopened.save(AnsweringMode.MANUAL,saved.accountKey,true)
            assertEquals(AnsweringMode.MANUAL,first.load().mode)
            assertEquals(outgoing,CampaignAuthorizationStore(context).load())
            assertEquals(profile,AudioEngineProfileStore(context).load())
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun settingsDoNotEnableAiOrChangeTaskAndEnginesOnOpen() {
        val before=IncomingAssistantPolicyStore(context).preferences.all.toMap()
        val outgoing=CampaignAuthorizationStore(context).load()
        val profile=AudioEngineProfileStore(context).load()
        ActivityScenario.launch(IncomingAssistantSettingsActivity::class.java).use { }
        assertEquals(before,IncomingAssistantPolicyStore(context).preferences.all.toMap())
        assertEquals(outgoing,CampaignAuthorizationStore(context).load())
        assertEquals(profile,AudioEngineProfileStore(context).load())
    }

    @Test fun rejectedAudioLaunchFinalizesIsolatedEvidenceWithoutCallOrInference() {
        val directory=File(context.cacheDir,"incoming-preflight-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(context) { override fun getFilesDir():File=directory }
        try {
            val recorder=checkNotNull(CallSessionEvidenceRecorder.create(isolated))
            val configuration=ConversationRunConfiguration.ModelDirect(AudioEngineProfile.STABLE,
                ConversationInstructionBuilder.build(ConversationPreparation.incoming(),IncomingSecretaryScenario.GOAL),ConversationPreparation.incoming())
            instrumentation.runOnMainSync {
                assertNotEquals(AudioManager.MODE_IN_CALL,context.getSystemService(AudioManager::class.java).mode)
                val result=AndroidConversationRuntime.run(context,"incoming-preflight",AndroidConversationRuntime.TASK_SCOPED_TURNS,
                    IncomingSecretaryScenario(context,configuration),liveAuthorization={false},callStillActive={false},
                    evidenceRecorder=recorder,runConfiguration=configuration) { report ->
                    assertTrue(report.contains("failure_reason=cellular_call_not_active"))
                }
                assertNull(result)
            }
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
            while(!File(recorder.directory,"COMPLETE").isFile && System.nanoTime()<deadline) Thread.sleep(10)
            assertTrue(File(recorder.directory,"COMPLETE").isFile)
        } finally {directory.deleteRecursively()}
    }

    private class Input : SpeechInputProvider {
        var starts=0
        override fun start(listener:SpeechInputProvider.Listener) { starts++ }
        override fun writePcm(bytes:ByteArray,offset:Int,length:Int)=true
        override fun finishInput()=Unit
        override fun cancel()=Unit
        override fun close()=Unit
    }
    private class Output : SpeechOutputProvider {
        var calls=0
        var listener:SpeechOutputProvider.Listener?=null
        override fun synthesize(text:String,listener:SpeechOutputProvider.Listener) {calls++;this.listener=listener}
        override fun cancel()=Unit
        override fun close()=Unit
    }
    private class Backend : TextCallAgentBackend {
        var calls=0
        override fun generate(userText:String,listener:TextCallAgentBackend.Listener) {calls++}
        override fun cancel()=Unit
    }
    private class Listener(val approved:()->Unit={}) : LocalSpeechTextPipeline.Listener {
        var texts=0;var pcm=0
        override fun onSpeechInputReady()=error("greeting_must_not_start_stt")
        override fun onUserTranscript(text:String)=error("greeting_is_not_caller_transcript")
        override fun onApprovedText(text:String) {texts++;approved()}
        override fun onOutputPcm16Mono16k(pcm:ByteArray) {this.pcm++}
        override fun onDroppedText()=error("unexpected_drop")
        override fun onError(reason:String)=error("unexpected_error")
    }
    @Test fun openingUsesApprovalAndTtsWithoutInventedSttOrModelTurn() {
        instrumentation.runOnMainSync {
            val input=Input();val output=Output();val backend=Backend();val listener=Listener()
            val pipeline=LocalSpeechTextPipeline(context,backend,TextOutputApprovalPolicy {TextOutputDecision.RELEASE},input=input,output=output)
            pipeline.use {
                it.speakCandidate("Dzień dobry, tu wirtualna asystentka.",listener)
                assertEquals(1,listener.texts);assertEquals(0,input.starts);assertEquals(0,backend.calls)
                output.listener!!.onPcm16Mono16k(ByteArray(320))
                assertEquals(1,listener.pcm)
            }
        }
    }
    @Test fun takeoverAtApprovalPreventsSynthesisAndLatePcmAfterCancelIsDropped() {
        instrumentation.runOnMainSync {
            val output=Output()
            val pipeline=LocalSpeechTextPipeline(context,Backend(),TextOutputApprovalPolicy {TextOutputDecision.RELEASE},input=Input(),output=output)
            pipeline.use {
                val revoked=Listener {it.cancel()}
                it.speakCandidate("Dzień dobry.",revoked)
                assertEquals(0,output.calls)
                val later=Listener()
                it.speakCandidate("W jakiej sprawie Państwo dzwonią?",later)
                val delayed=output.listener!!
                it.cancel()
                delayed.onPcm16Mono16k(ByteArray(320))
                assertEquals(0,later.pcm)
            }
        }
    }
    @Test fun selectedLocalModelHandlesIncomingSecretaryContextWithoutCallOrTools() {
        val profile=checkNotNull(AudioEngineProfileStore(context).load())
        val instruction=ConversationInstructionBuilder.build(ConversationPreparation.incoming(),IncomingSecretaryScenario.GOAL)
        val backend=DialogueEngineProviders.resolve(profile).createDirect(context,instruction)
        try {
            listOf("Chciałbym zostawić wiadomość: proszę o kontakt w sprawie przeglądu samochodu.",
                "Dziękuję. Czy zapisałaś moją sprawę?").forEach { utterance ->
                val done=CountDownLatch(1);val result=AtomicReference<String>();val failure=AtomicReference<String>()
                backend.generate(utterance,object:TextCallAgentBackend.Listener {
                    override fun onComplete(text:String) {result.set(text);done.countDown()}
                    override fun onError(reason:String) {failure.set(reason);done.countDown()}
                })
                assertTrue("incoming_model_timeout",done.await(120,TimeUnit.SECONDS))
                assertNull("incoming_model_error",failure.get())
                val text=result.get().orEmpty()
                assertTrue("incoming_model_empty",text.isNotBlank())
                assertFalse("incoming_model_control_token",text.contains("[["))
                assertFalse("incoming_model_json",text.trimStart().startsWith("{"))
            }
            println("INCOMING_NATIVE_MODEL_TURNS=2; external_effects=false; call=false")
        } finally {backend.close()}
    }
}
