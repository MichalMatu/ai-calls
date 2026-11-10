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
    fun naturalPauseDoesNotEndLiveStyleUtterance() {
        val detector = PcmEndOfUtteranceDetector(
            trailingSilenceMs = 1_500,
            maxCaptureMs = 60_000,
        )

        assertFalse(detector.acceptPcm16(pcm(600, 3_000)).shouldStop)
        assertFalse(detector.acceptPcm16(pcm(900, 0)).shouldStop)
        assertFalse(detector.acceptPcm16(pcm(800, 3_000)).shouldStop)

        val result = detector.acceptPcm16(pcm(1_600, 0))

        assertTrue(result.shouldStop)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.TRAILING_SILENCE, result.reason)
        assertTrue(result.speechDetected)
        assertEquals(3_800L, result.capturedMs)
        assertEquals(2_300L, result.estimatedSpeechEndMs)
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

    @Test
    fun interruptedRemoteCallBeforeTrailingSilenceDoesNotFinishSttInput() {
        // Synthetic frame layout matches the diagnosed 17.08 s RX failure mode,
        // without storing or replaying the user's private audio.
        val detector = PcmEndOfUtteranceDetector(
            trailingSilenceMs = 1_500, maxCaptureMs = 60_000,
        )
        val segments = listOf(
            5_000 to 0, // initial IVR gap is not an end-of-speech trigger
            3_200 to 3_000,
            520 to 0,
            4_600 to 3_000,
            760 to 0,
            2_000 to 3_000,
            1_000 to 0, // remote Call ends, still shy of 1,500 ms VAD endpoint
        )
        var last = detector.acceptPcm16(ByteArray(0))
        segments.forEach { (duration, sample) ->
            last = detector.acceptPcm16(pcm(duration, sample))
        }
        assertFalse(last.shouldStop)
        assertEquals(null, last.reason)
        assertTrue(last.speechDetected)
        assertEquals(17_080L, last.capturedMs)
        assertEquals(1_000L, last.capturedMs - checkNotNull(last.estimatedSpeechEndMs))
        // The production teardown cancels OnDeviceSpeechInput here; no STT final.
        val finalized = detector.acceptPcm16(pcm(500, 0))
        assertTrue(finalized.shouldStop)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.TRAILING_SILENCE, finalized.reason)
    }

    @Test
    fun continuousAppointmentSpeechIsSegmentedAtTwentySeconds() {
        val appointment = PcmEndOfUtteranceDetector(
            trailingSilenceMs = 1_500, maxCaptureMs = 20_000,
        )
        assertFalse(appointment.acceptPcm16(pcm(19_500, 3_000)).shouldStop)
        val endpoint = appointment.acceptPcm16(pcm(700, 3_000))
        assertTrue(endpoint.shouldStop)
        assertTrue(endpoint.speechDetected)
        assertEquals(PcmEndOfUtteranceDetector.EndReason.MAX_DURATION, endpoint.reason)
        assertEquals(20_000L, endpoint.capturedMs)

        val generic = PcmEndOfUtteranceDetector(
            trailingSilenceMs = 1_500, maxCaptureMs = 60_000,
        )
        assertFalse(generic.acceptPcm16(pcm(20_000, 3_000)).shouldStop)
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
