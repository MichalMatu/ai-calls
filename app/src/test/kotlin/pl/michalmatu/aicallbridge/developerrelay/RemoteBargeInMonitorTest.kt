package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBargeInMonitorTest {
    private fun frame(amplitude: Short): ByteArray = ByteArray(640).also { bytes ->
        for (i in bytes.indices step 2) {
            bytes[i] = (amplitude.toInt() and 255).toByte()
            bytes[i + 1] = (amplitude.toInt() shr 8).toByte()
        }
    }

    @Test fun continuousRemoteSpeechInterruptsExactlyOnceAfterThreshold() {
        var fired = 0
        val monitor = RemoteBargeInMonitor(onVoice = { fired++ })
        repeat(14) { assertTrue(monitor.onFrame(frame(1_600), 640)) }
        assertEquals(0, fired)
        assertTrue(monitor.onFrame(frame(1_600), 640))
        repeat(6) { monitor.onFrame(frame(1_600), 640) }
        assertEquals(1, fired)
        val pcm = monitor.freezeAndDrain()
        assertEquals(21 * 640, pcm.size)
        assertFalse(monitor.onFrame(frame(1_600), 640))
        assertEquals(0, monitor.freezeAndDrain().size)
    }

    @Test fun echoSizedOrIntermittentNoiseNeverTriggers() {
        var fired = 0
        val monitor = RemoteBargeInMonitor(onVoice = { fired++ })
        repeat(30) {
            monitor.onFrame(frame(750), 640)
            monitor.onFrame(frame(0), 640)
            monitor.onFrame(frame(1_300), 640)
        }
        assertEquals(0, fired)
    }

    @Test fun preRollKeepsOnlyRecentBoundedPcmAndTransfersInOrder() {
        val monitor = RemoteBargeInMonitor(onVoice = {}, minVoiceFrames = 500, maxBufferedBytes = 1_280)
        val first = frame(100)
        val second = frame(200)
        val third = frame(300)
        monitor.onFrame(first, first.size)
        monitor.onFrame(second, second.size)
        monitor.onFrame(third, third.size)
        assertArrayEquals(second + third, monitor.freezeAndDrain())
    }

    @Test fun errorNotifiesOnceAndDiscardsStalePreRollWithoutTriggeringVoice() {
        var voiced = 0
        val failures = mutableListOf<String>()
        val monitor = RemoteBargeInMonitor(
            onVoice = { voiced++ },
            onUnavailable = { failures += it },
        )
        repeat(12) { assertTrue(monitor.onFrame(frame(1_700), 640)) }
        monitor.onError("downlink_eof")
        monitor.onError("late_duplicate")
        assertEquals(listOf("downlink_eof"), failures)
        assertFalse(monitor.onFrame(frame(1_700), 640))
        assertEquals(0, voiced)
        assertEquals(0, monitor.freezeAndDrain().size)
    }

    @Test fun abandonedOrTransferredMonitorNeverReportsAnObsoleteError() {
        val failures = mutableListOf<String>()
        val monitor = RemoteBargeInMonitor(
            onVoice = {},
            onUnavailable = { failures += it },
        )
        monitor.onFrame(frame(1_500), 640)
        assertEquals(640, monitor.freezeAndDrain().size)
        monitor.onError("downlink_pump_closed")
        monitor.onFinished()
        assertTrue(failures.isEmpty())
    }

    @Test fun splitPcmSamplesStillReachVoiceThreshold() {
        var fired = 0
        val monitor = RemoteBargeInMonitor(onVoice = { fired++ }, minVoiceFrames = 2)
        val voice = frame(1_500)
        repeat(2) {
            assertTrue(monitor.onFrame(voice.copyOfRange(0, 1), 1))
            assertTrue(monitor.onFrame(voice.copyOfRange(1, 640), 639))
        }
        assertEquals(1, fired)
    }
}
