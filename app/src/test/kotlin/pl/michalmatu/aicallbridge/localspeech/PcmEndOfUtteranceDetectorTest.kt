package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmEndOfUtteranceDetectorTest {
    @Test
    fun stopsAfterConfirmedSpeechAndTrailingSilence() {
        val detector = PcmEndOfUtteranceDetector()

        assertFalse(detector.acceptPcm16(pcm(500, 0)).shouldStop)
        assertFalse(detector.acceptPcm16(pcm(1_000, 3_000)).shouldStop)
        assertFalse(detector.acceptPcm16(pcm(600, 0)).shouldStop)

        val result = detector.acceptPcm16(pcm(100, 0))

        assertTrue(result.shouldStop)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.TRAILING_SILENCE, result.reason)
        assertTrue(result.speechDetected)
        assertEquals(2_200L, result.capturedMs)
        assertEquals(1_500L, result.estimatedSpeechEndMs)
    }

    @Test
    fun silenceOnlyFallsBackToHardMaximum() {
        val detector = PcmEndOfUtteranceDetector()

        assertFalse(detector.acceptPcm16(pcm(7_900, 0)).shouldStop)
        val result = detector.acceptPcm16(pcm(200, 0))

        assertTrue(result.shouldStop)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.MAX_DURATION, result.reason)
        assertFalse(result.speechDetected)
        assertEquals(8_000L, result.capturedMs)
        assertEquals(null, result.estimatedSpeechEndMs)
    }

    @Test
    fun shortNoiseBurstDoesNotArmTrailingSilenceEndpoint() {
        val detector = PcmEndOfUtteranceDetector()

        assertFalse(detector.acceptPcm16(pcm(100, 4_000)).shouldStop)
        assertFalse(detector.acceptPcm16(pcm(1_000, 0)).shouldStop)

        val result = detector.acceptPcm16(pcm(7_000, 0))

        assertTrue(result.shouldStop)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.MAX_DURATION, result.reason)
        assertFalse(result.speechDetected)
    }

    @Test
    fun handlesPcmSamplesSplitAcrossOddByteBoundaries() {
        val detector = PcmEndOfUtteranceDetector(
            minSpeechMs = 40,
            trailingSilenceMs = 40,
            maxCaptureMs = 1_000,
        )
        val speech = pcm(60, 3_000)
        val silence = pcm(60, 0)

        var snapshot = detector.acceptPcm16(speech, 0, 1)
        snapshot = detector.acceptPcm16(speech, 1, speech.size - 1)
        assertFalse(snapshot.shouldStop)
        snapshot = detector.acceptPcm16(silence, 0, 3)
        snapshot = detector.acceptPcm16(silence, 3, silence.size - 3)

        assertTrue(snapshot.shouldStop)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.TRAILING_SILENCE, snapshot.reason)
        assertTrue(snapshot.speechDetected)
        assertNotNull(snapshot.estimatedSpeechEndMs)
    }

    private fun pcm(durationMs: Int, sample: Int): ByteArray {
        val sampleCount = LocalSpeechFormat.SAMPLE_RATE_HZ * durationMs / 1_000
        val bytes = ByteArray(sampleCount * 2)
        val value = sample.toShort().toInt()
        var offset = 0
        repeat(sampleCount) {
            bytes[offset] = (value and 0xff).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
            offset += 2
        }
        return bytes
    }
}
