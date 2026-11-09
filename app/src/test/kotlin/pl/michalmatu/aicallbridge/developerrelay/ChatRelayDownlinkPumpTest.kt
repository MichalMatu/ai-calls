package pl.michalmatu.aicallbridge.developerrelay

import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRelayDownlinkPumpTest {
    @Test
    fun `continuously drains inactive audio and capture receives only fresh bytes`() {
        val input = PipedInputStream(4_096)
        val output = PipedOutputStream(input)
        val pump = ChatRelayDownlinkPump(input, frameBytes = 64)
        try {
            pump.start()

            val staleBeforeFirstTurn = byteArrayOf(1, 2, 3, 4)
            output.write(staleBeforeFirstTurn)
            output.flush()
            waitUntil { pump.discardedBytes() >= staleBeforeFirstTurn.size }

            val first = ByteArrayOutputStream()
            val firstDone = CountDownLatch(1)
            pump.beginCapture(captureInto(first, expectedBytes = 4, firstDone))
            output.write(byteArrayOf(10, 11, 12, 13))
            output.flush()
            assertTrue(firstDone.await(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(10, 11, 12, 13), first.toByteArray())

            val staleBetweenTurns = byteArrayOf(20, 21, 22, 23, 24)
            val discardedBeforeGap = pump.discardedBytes()
            output.write(staleBetweenTurns)
            output.flush()
            waitUntil { pump.discardedBytes() >= discardedBeforeGap + staleBetweenTurns.size }

            val second = ByteArrayOutputStream()
            val secondDone = CountDownLatch(1)
            pump.beginCapture(captureInto(second, expectedBytes = 3, secondDone))
            output.write(byteArrayOf(30, 31, 32))
            output.flush()
            assertTrue(secondDone.await(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(30, 31, 32), second.toByteArray())
        } finally {
            pump.close()
            output.close()
        }
    }

    @Test
    fun `only one capture can own fresh downlink frames`() {
        val input = PipedInputStream(1_024)
        val output = PipedOutputStream(input)
        val pump = ChatRelayDownlinkPump(input, frameBytes = 64)
        try {
            pump.start()
            val done = CountDownLatch(1)
            pump.beginCapture(captureInto(ByteArrayOutputStream(), expectedBytes = 1, done))
            val error = runCatching {
                pump.beginCapture(captureInto(ByteArrayOutputStream(), expectedBytes = 1, CountDownLatch(1)))
            }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            output.write(byteArrayOf(7))
            output.flush()
            assertTrue(done.await(1, TimeUnit.SECONDS))
        } finally {
            pump.close()
            output.close()
        }
    }

    @Test
    fun `STT no match can abandon active RX capture and listen again on same pump`() {
        val input = PipedInputStream(4_096)
        val output = PipedOutputStream(input)
        val pump = ChatRelayDownlinkPump(input, frameBytes = 64)
        val firstFrame = CountDownLatch(1)
        val abandoned = CountDownLatch(1)
        var firstFinished = false
        var firstErrored = false
        try {
            pump.start()
            pump.beginCapture(object : ChatRelayDownlinkPump.Capture {
                override fun onFrame(bytes: ByteArray, length: Int): Boolean {
                    firstFrame.countDown()
                    return true
                }

                override fun onFinished() { firstFinished = true }
                override fun onError(reason: String) { firstErrored = true }
                override fun onAbandoned() { abandoned.countDown() }
            })
            output.write(byteArrayOf(1, 2, 3))
            output.flush()
            assertTrue(firstFrame.await(1, TimeUnit.SECONDS))
            assertTrue(pump.abandonActiveCapture())
            assertTrue(abandoned.await(1, TimeUnit.SECONDS))
            assertFalse(pump.abandonActiveCapture())

            val next = ByteArrayOutputStream()
            val nextDone = CountDownLatch(1)
            pump.beginCapture(captureInto(next, expectedBytes = 3, nextDone))
            output.write(byteArrayOf(10, 11, 12))
            output.flush()
            assertTrue(nextDone.await(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(10, 11, 12), next.toByteArray())
            assertFalse(firstFinished)
            assertFalse(firstErrored)
        } finally {
            pump.close()
            output.close()
        }
    }

    @Test
    fun `late in-flight frame from abandoned recognizer cannot cancel next RX capture`() {
        val input = PipedInputStream(4_096)
        val output = PipedOutputStream(input)
        val pump = ChatRelayDownlinkPump(input, frameBytes = 64)
        val enteredOldFrame = CountDownLatch(1)
        val releaseOldFrame = CountDownLatch(1)
        val oldErrors = CountDownLatch(1)
        try {
            pump.start()
            pump.beginCapture(object : ChatRelayDownlinkPump.Capture {
                override fun onFrame(bytes: ByteArray, length: Int): Boolean {
                    enteredOldFrame.countDown()
                    releaseOldFrame.await(1, TimeUnit.SECONDS)
                    // An old callback returning false must not finish a newer capture.
                    return false
                }

                override fun onFinished() = Unit
                override fun onError(reason: String) { oldErrors.countDown() }
            })
            output.write(byteArrayOf(1, 2))
            output.flush()
            assertTrue(enteredOldFrame.await(1, TimeUnit.SECONDS))
            assertTrue(pump.abandonActiveCapture())

            val next = ByteArrayOutputStream()
            val nextDone = CountDownLatch(1)
            pump.beginCapture(captureInto(next, expectedBytes = 3, nextDone))
            releaseOldFrame.countDown()
            output.write(byteArrayOf(30, 31, 32))
            output.flush()
            assertTrue(nextDone.await(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(30, 31, 32), next.toByteArray())
            assertFalse(oldErrors.await(50, TimeUnit.MILLISECONDS))
        } finally {
            releaseOldFrame.countDown()
            pump.close()
            output.close()
        }
    }

    private fun captureInto(
        target: ByteArrayOutputStream,
        expectedBytes: Int,
        done: CountDownLatch,
    ) = object : ChatRelayDownlinkPump.Capture {
        override fun onFrame(bytes: ByteArray, length: Int): Boolean {
            target.write(bytes, 0, length)
            return target.size() < expectedBytes
        }

        override fun onFinished() {
            done.countDown()
        }

        override fun onError(reason: String) {
            throw AssertionError("unexpected capture error: $reason")
        }
    }

    @Test fun abandoningOldMonitorCannotDetachNewCapture() {
        val input = PipedInputStream(4_096)
        val output = PipedOutputStream(input)
        val pump = ChatRelayDownlinkPump(input, frameBytes = 64)
        try {
            pump.start()
            val old = captureInto(ByteArrayOutputStream(), 1, CountDownLatch(1))
            val next = captureInto(ByteArrayOutputStream(), 1, CountDownLatch(1))
            pump.beginCapture(old)
            assertTrue(pump.abandonCapture(old))
            pump.beginCapture(next)
            assertFalse(pump.abandonCapture(old))
            assertTrue(pump.abandonCapture(next))
        } finally {
            pump.close()
            output.close()
        }
    }

    @Test fun downlinkEofRevokesCaptureAndForbidsNewListeningTurn() {
        val input = PipedInputStream(4_096)
        val output = PipedOutputStream(input)
        val pump = ChatRelayDownlinkPump(input, frameBytes = 640)
        val failed = CountDownLatch(1)
        var reason: String? = null
        try {
            pump.start()
            val monitor = RemoteBargeInMonitor(
                onVoice = {},
                onUnavailable = {
                    reason = it
                    failed.countDown()
                },
            )
            pump.beginCapture(monitor)
            output.write(ByteArray(640))
            output.flush()
            output.close()
            assertTrue(failed.await(1, TimeUnit.SECONDS))
            assertTrue(pump.isSourceTerminated())
            assertTrue(reason == "downlink_eof")
            assertTrue(runCatching {
                pump.beginCapture(RemoteBargeInMonitor(onVoice = {}))
            }.isFailure)
            assertFalse(pump.abandonCapture(monitor))
        } finally {
            pump.close()
            output.close()
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        assertTrue("condition was not reached before timeout", predicate())
    }
}
