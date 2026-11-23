package pl.michalmatu.aicallbridge.data.adapters

import android.content.Context
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.SpeechInputId
import pl.michalmatu.aicallbridge.audioengines.SpeechOutputId
import pl.michalmatu.aicallbridge.audioengines.AndroidSttInputProvider
import pl.michalmatu.aicallbridge.audioengines.ParakeetSpeechInputProvider
import pl.michalmatu.aicallbridge.audioengines.GemmaAudioDirectInputProvider
import pl.michalmatu.aicallbridge.audioengines.AndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.audioengines.PiperAndroidTtsOutputProvider
import pl.michalmatu.aicallbridge.localspeech.LocalSpeechTextPipeline
import pl.michalmatu.aicallbridge.textagent.TextCallAgentBackend
import pl.michalmatu.aicallbridge.textagent.TextOutputApprovalPolicy

/**
 * Android infrastructure composition of an authorized local conversation turn.
 * Runtime controls Call ownership, STOP, cancellation and playback completion;
 * this adapter only wires STT/TTS providers to the existing approved pipeline.
 * Unsupported output providers must fail closed, never fall back silently.
 */
internal object AndroidConversationSpeechPipelineFactory {
    fun create(
        context: Context,
        backend: TextCallAgentBackend,
        approvalPolicy: TextOutputApprovalPolicy,
        profile: AudioEngineProfile,
    ): LocalSpeechTextPipeline = LocalSpeechTextPipeline(
        context,
        backend,
        approvalPolicy,
        languageTag = profile.languageTag,
        voiceId = profile.voiceId,
        input = when (profile.input) {
            SpeechInputId.ANDROID_STT -> AndroidSttInputProvider(context, profile.languageTag)
            SpeechInputId.PARAKEET_PL -> ParakeetSpeechInputProvider(context)
            SpeechInputId.GEMMA_AUDIO_DIRECT -> GemmaAudioDirectInputProvider()
        },
        output = when (profile.output) {
            SpeechOutputId.ANDROID_TTS_PL -> AndroidTtsOutputProvider(
                context, profile.languageTag, profile.voiceId, profile.effectiveTtsEnginePackage,
            )
            SpeechOutputId.PIPER_PL -> PiperAndroidTtsOutputProvider(
                context, profile.languageTag, profile.voiceId,
            )
            SpeechOutputId.KOKORO, SpeechOutputId.POCKET_TTS ->
                error("unavailable_polish_output_engine")
        },
    )
}
