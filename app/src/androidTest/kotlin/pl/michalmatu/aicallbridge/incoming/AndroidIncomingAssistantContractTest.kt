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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.json.JSONArray
import pl.michalmatu.aicallbridge.developerrelay.CallSessionPcmWav
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
    @Test fun selectedModelAndVoiceRunSecretaryGreetingAndDialogWithoutTelephony() {
        val profile=checkNotNull(AudioEngineProfileStore(context).load())
        val preparation=ConversationPreparation.incoming()
        val configuration=ConversationRunConfiguration.ModelDirect(profile,
            ConversationInstructionBuilder.build(preparation,IncomingSecretaryScenario.GOAL),preparation)
        val authorized=AtomicBoolean(true)
        val turn=AtomicInteger(0)
        val input=object:SpeechInputProvider {
            var listener:SpeechInputProvider.Listener?=null
            var starts=0
            override fun start(listener:SpeechInputProvider.Listener) {starts++;this.listener=listener;listener.onReady()}
            override fun writePcm(bytes:ByteArray,offset:Int,length:Int)=error("offcall_text_fixture_has_no_pcm_input")
            override fun finishInput()=Unit
            override fun cancel() {listener=null}
            override fun close()=cancel()
            fun emit(text:String) {checkNotNull(listener).onFinalTranscript(text)}
        }
        val directory=File(context.cacheDir,"incoming-synthetic-flow/${UUID.randomUUID()}").apply {check(mkdirs())}
        val evidence=JSONArray()
        lateinit var dialogue:ConversationDialogueSession
        lateinit var pipeline:LocalSpeechTextPipeline
        instrumentation.runOnMainSync {
            dialogue=IncomingSecretaryScenario(context,configuration).open(ConversationRuntimeHooks(
                authorized={authorized.get()},mediaGeneration={0L},turn={turn.get()},emit={},
                decisionSink=ConversationDecisionSink {_,_,_,_,_->}))
            dialogue.start()
            val output=when(profile.output) {
                SpeechOutputId.ANDROID_TTS_PL -> AndroidTtsOutputProvider(context,profile.languageTag,profile.voiceId)
                SpeechOutputId.PIPER_PL -> PiperAndroidTtsOutputProvider(context,profile.languageTag,profile.voiceId)
                SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS -> error("speech_output_not_integrated")
            }
            pipeline=LocalSpeechTextPipeline(context,dialogue.backend,dialogue.approvalPolicy,input=input,output=output)
        }
        fun execute(number:Int,transcript:String?,expectDrop:Boolean=false) {
            turn.set(number)
            val done=CountDownLatch(1)
            val error=AtomicReference<String>()
            val approved=AtomicReference<String>()
            val pcm=AtomicReference<ByteArray>()
            val heard=AtomicInteger(0)
            val dropped=AtomicBoolean(false)
            val started=System.nanoTime()
            val listener=object:LocalSpeechTextPipeline.Listener {
                override fun onSpeechInputReady()=Unit
                override fun onUserTranscript(text:String) {heard.incrementAndGet();dialogue.onTranscript(number,text)}
                override fun onApprovedText(text:String) {approved.set(text)}
                override fun onOutputPcm16Mono16k(bytes:ByteArray) {pcm.set(bytes);done.countDown()}
                override fun onDroppedText() {dropped.set(true);done.countDown()}
                override fun onError(reason:String) {error.set(reason);done.countDown()}
            }
            instrumentation.runOnMainSync {
                if(transcript==null) pipeline.speakCandidate(checkNotNull(dialogue.openingText),listener)
                else {pipeline.start(listener);input.emit(transcript)}
            }
            assertTrue("incoming_speech_flow_timeout",done.await(120,TimeUnit.SECONDS))
            assertNull("incoming_speech_flow_error",error.get())
            if(expectDrop) {assertTrue(dropped.get());assertNull(pcm.get());return}
            assertFalse("incoming_unexpected_drop",dropped.get())
            assertEquals(if(transcript==null) 0 else 1,heard.get())
            val bytes=checkNotNull(pcm.get())
            assertTrue("incoming_pcm_empty",bytes.isNotEmpty())
            assertEquals(0,bytes.size%2)
            assertTrue("incoming_pcm_silent",bytes.any {it.toInt()!=0})
            val file=File(directory,"turn-$number.wav")
            CallSessionPcmWav.write(file,bytes)
            evidence.put(JSONObject().put("turn",number).put("counterpart",transcript ?: JSONObject.NULL)
                .put("approved",approved.get()).put("wav",file.name).put("pcm_bytes",bytes.size)
                .put("elapsed_ms",(System.nanoTime()-started)/1_000_000L).put("synthetic_input",true))
        }
        try {
            execute(0,null)
            assertEquals(0,input.starts)
            execute(1,"Chciałbym zostawić wiadomość: proszę o kontakt w sprawie przeglądu samochodu.")
            execute(2,"Dziękuję. To cała wiadomość, do widzenia.")
            authorized.set(false)
            execute(3,null,expectDrop=true)
            File(directory,"metadata.json").writeText(JSONObject().put("dialogue",profile.dialogue.name)
                .put("output",profile.output.name).put("voice",profile.voiceId ?: "auto")
                .put("call",false).put("real_stt",false).put("real_model",true).put("real_tts",true)
                .put("revocation_dropped",true).put("turns",evidence).toString())
            println("INCOMING_OFFCALL_FLOW_EVIDENCE=cache/incoming-synthetic-flow/${directory.name}")
            println("INCOMING_OFFCALL_FLOW=PASS; model_turns=2; native_tts_segments=3; call=false; stt=synthetic")
        } finally {
            authorized.set(false)
            instrumentation.runOnMainSync {pipeline.close();dialogue.close()}
        }
    }
}
