package pl.michalmatu.aicallbridge.developerrelay

import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
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

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        assertTrue("condition was not reached before timeout", predicate())
    }
}
