package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import pl.michalmatu.aicallbridge.localspeech.LocalTtsSpeechOutput
import pl.michalmatu.aicallbridge.localspeech.OnDeviceSpeechInput

/** Transport-independent 16-kHz mono PCM16 input. It never owns Telecom or the grant. */
internal interface SpeechInputProvider : AutoCloseable {
    interface Listener {
        fun onReady()
        fun onFinalTranscript(text: String)
        fun onError(reason: String)
    }
    fun start(listener: Listener)
    fun writePcm(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean
    fun finishInput()
    fun cancel()
}

/** Output still passes through TextCallTurnController approval and owned-call TX. */
internal interface SpeechOutputProvider : AutoCloseable {
    interface Listener {
        fun onPcm16Mono16k(pcm: ByteArray)
        fun onError(reason: String)
    }
    fun synthesize(text: String, listener: Listener)
    fun cancel()
}

/** Preserve the shipped Android recognizer and its timing/segmentation unchanged. */
internal class AndroidSttInputProvider(context: Context, languageTag: String) : SpeechInputProvider {
    private val delegate = OnDeviceSpeechInput(context, languageTag)
    override fun start(listener: SpeechInputProvider.Listener) = delegate.start(
        object : OnDeviceSpeechInput.Listener {
            override fun onReady() = listener.onReady()
            override fun onFinalTranscript(text: String) = listener.onFinalTranscript(text)
            override fun onError(reason: String) = listener.onError(reason)
        },
    )
    override fun writePcm(bytes: ByteArray, offset: Int, length: Int): Boolean =
        delegate.writePcm(bytes, offset, length)
    override fun finishInput() = delegate.finishInput()
    override fun cancel() = delegate.cancel()
    override fun close() = delegate.close()
}

/** Preserve the shipped local TTS PCM conversion; an optional voice is selected by exact ID. */
internal class AndroidTtsOutputProvider(
    context: Context,
    languageTag: String,
    voiceId: String?,
) : SpeechOutputProvider {
    private val delegate = LocalTtsSpeechOutput(context, languageTag, voiceId)
    override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) =
        delegate.synthesize(text, object : LocalTtsSpeechOutput.Listener {
            override fun onPcm16Mono16k(pcm: ByteArray) = listener.onPcm16Mono16k(pcm)
            override fun onError(reason: String) = listener.onError(reason)
        })
    override fun cancel() = delegate.cancel()
    override fun close() = delegate.close()
}

/** GPL-licensed native Piper implementation is external to AI Calls; only Android TTS IPC. */
internal object PiperServiceContract {
    const val PACKAGE = "dev.ihorshevchuk.piper"
}

internal class PiperAndroidTtsOutputProvider(
    context: Context,
    languageTag: String,
    voiceId: String?,
) : SpeechOutputProvider {
    private val delegate = LocalTtsSpeechOutput(
        context, languageTag, voiceId, enginePackage = PiperServiceContract.PACKAGE,
    )
    override fun synthesize(text: String, listener: SpeechOutputProvider.Listener) =
        delegate.synthesize(text, object : LocalTtsSpeechOutput.Listener {
            override fun onPcm16Mono16k(pcm: ByteArray) = listener.onPcm16Mono16k(pcm)
            override fun onError(reason: String) = listener.onError(reason)
        })
    override fun cancel() = delegate.cancel()
    override fun close() = delegate.close()
}
