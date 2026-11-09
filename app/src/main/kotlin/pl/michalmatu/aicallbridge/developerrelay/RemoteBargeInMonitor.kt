package pl.michalmatu.aicallbridge.developerrelay

import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Bounded RX speech-start probe while a local TTS reply is in flight.
 * Speech must persist across multiple PCM frames; it is not semantic VAD or echo cancellation.
 * Retained audio is kept only in RAM for the next authorized STT turn.
 */
internal class RemoteBargeInMonitor(
    private val onVoice: () -> Unit,
    private val onUnavailable: (String) -> Unit = {},
    private val thresholdRms: Int = 900,
    private val minVoiceFrames: Int = 15,
    private val maxBufferedBytes: Int = 32_000,
) : ChatRelayDownlinkPump.Capture {
    init {
        require(thresholdRms > 0 && minVoiceFrames > 0 && maxBufferedBytes > 0)
    }

    private val lock = Any()
    private val frames = ArrayDeque<ByteArray>()
    private var retainedBytes = 0
    private var pendingLow: Int? = null
    private var frameSamples = 0
    private var frameSquares = 0L
    private var voicedFrames = 0
    private var detected = false
    private var frozen = false

    override fun onFrame(bytes: ByteArray, length: Int): Boolean {
        require(length >= 0 && length <= bytes.size)
        var newlyDetected = false
        synchronized(lock) {
            if (frozen) return false
            if (length == 0) return true
            val frame = bytes.copyOfRange(0, length)
            frames.addLast(frame)
            retainedBytes += length
            while (retainedBytes > maxBufferedBytes && frames.isNotEmpty()) {
                retainedBytes -= frames.removeFirst().size
            }

            var index = 0
            pendingLow?.let { low ->
                acceptSample(low or (bytes[0].toInt() shl 8))
                pendingLow = null
                index = 1
            }
            while (index + 1 < length) {
                val sample = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
                acceptSample(sample)
                index += 2
            }
            if (index < length) pendingLow = bytes[index].toInt() and 0xff
            if (!detected && voicedFrames >= minVoiceFrames) {
                detected = true
                newlyDetected = true
            }
        }
        if (newlyDetected) onVoice()
        return true
    }

    /** Freeze at transfer time; frames after a frozen capture cannot join a newer turn. */
    fun freezeAndDrain(): ByteArray = synchronized(lock) {
        frozen = true
        val pcm = ByteArray(retainedBytes)
        var offset = 0
        while (frames.isNotEmpty()) {
            val frame = frames.removeFirst()
            frame.copyInto(pcm, offset)
            offset += frame.size
        }
        retainedBytes = 0
        pcm
    }

    override fun onFinished() = Unit
    override fun onError(reason: String) {
        // An EOF/error before transfer is a fatal downlink failure, not remote silence.
        // After freeze/transfer it belongs to an obsolete turn and must do nothing.
        val notify = synchronized(lock) {
            if (frozen) false else {
                frozen = true
                frames.clear()
                retainedBytes = 0
                true
            }
        }
        if (notify) onUnavailable(reason)
    }

    private fun acceptSample(raw: Int) {
        val sample = raw.toShort().toLong()
        frameSquares += sample * sample
        frameSamples++
        if (frameSamples == 320) { // 20 ms at 16 kHz, matching the Call PCM lease.
            val rms = sqrt(frameSquares.toDouble() / frameSamples).toInt()
            voicedFrames = if (rms >= thresholdRms) voicedFrames + 1 else 0
            frameSamples = 0
            frameSquares = 0L
        }
    }
}
