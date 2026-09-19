package pl.michalmatu.aicallbridge.localspeech

import kotlin.math.sqrt

/**
 * Lightweight PCM16 endpoint detector for one bounded conversational turn.
 *
 * It never replaces the hard capture limit: speech can finish early after a sustained trailing
 * silence, while no-speech/noisy input still terminates at [maxCaptureMs].
 */
internal class PcmEndOfUtteranceDetector(
    private val sampleRateHz: Int = LocalSpeechFormat.SAMPLE_RATE_HZ,
    frameMs: Int = 20,
    private val speechRmsThreshold: Int = 600,
    minSpeechMs: Int = 200,
    trailingSilenceMs: Int = 700,
    maxCaptureMs: Int = 8_000,
) {
    enum class EndReason {
        TRAILING_SILENCE,
        MAX_DURATION,
    }

    data class Snapshot(
        val shouldStop: Boolean,
        val reason: EndReason?,
        val capturedMs: Long,
        val speechDetected: Boolean,
        val estimatedSpeechEndMs: Long?,
    )

    private val samplesPerFrame = (sampleRateHz * frameMs / 1_000).coerceAtLeast(1)
    private val minSpeechSamples = durationToSamples(minSpeechMs)
    private val trailingSilenceSamples = durationToSamples(trailingSilenceMs)
    private val maxCaptureSamples = durationToSamples(maxCaptureMs)

    private var totalSamples = 0L
    private var voicedSamples = 0L
    private var lastVoicedEndSample: Long? = null
    private var frameSamples = 0
    private var frameSumSquares = 0L
    private var pendingLowByte: Int? = null
    private var endReason: EndReason? = null

    fun acceptPcm16(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Snapshot {
        require(offset >= 0 && length >= 0 && offset + length <= data.size)
        if (endReason != null || length == 0) return snapshot()

        var index = offset
        val end = offset + length
        pendingLowByte?.let { low ->
            if (index < end) {
                acceptSample(low or (data[index].toInt() shl 8))
                pendingLowByte = null
                index += 1
            }
        }

        while (index + 1 < end && endReason == null) {
            val sample = (data[index].toInt() and 0xff) or (data[index + 1].toInt() shl 8)
            acceptSample(sample)
            index += 2
        }
        if (index < end && endReason == null) {
            pendingLowByte = data[index].toInt() and 0xff
        }
        maybeStopAtMaxDuration()
        return snapshot()
    }

    private fun acceptSample(rawSample: Int) {
        if (endReason != null) return
        val sample = rawSample.toShort().toInt()
        frameSumSquares += sample.toLong() * sample.toLong()
        frameSamples += 1
        totalSamples += 1
        if (frameSamples >= samplesPerFrame) {
            finishFrame()
        }
        maybeStopAtMaxDuration()
    }

    private fun finishFrame() {
        val rms = sqrt(frameSumSquares.toDouble() / frameSamples.toDouble()).toInt()
        if (rms >= speechRmsThreshold) {
            voicedSamples += frameSamples.toLong()
            lastVoicedEndSample = totalSamples
        }
        frameSamples = 0
        frameSumSquares = 0L

        val lastVoice = lastVoicedEndSample
        if (
            endReason == null &&
            voicedSamples >= minSpeechSamples &&
            lastVoice != null &&
            totalSamples - lastVoice >= trailingSilenceSamples
        ) {
            endReason = EndReason.TRAILING_SILENCE
        }
    }

    private fun maybeStopAtMaxDuration() {
        if (endReason == null && totalSamples >= maxCaptureSamples) {
            endReason = EndReason.MAX_DURATION
        }
    }

    private fun snapshot(): Snapshot = Snapshot(
        shouldStop = endReason != null,
        reason = endReason,
        capturedMs = samplesToDurationMs(totalSamples),
        speechDetected = voicedSamples >= minSpeechSamples,
        estimatedSpeechEndMs = lastVoicedEndSample?.let(::samplesToDurationMs),
    )

    private fun durationToSamples(durationMs: Int): Long =
        sampleRateHz.toLong() * durationMs.toLong() / 1_000L

    private fun samplesToDurationMs(samples: Long): Long =
        samples * 1_000L / sampleRateHz.toLong()
}
