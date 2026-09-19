package pl.michalmatu.aicallbridge.developerrelay

import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-reader owner for the relay call downlink.
 *
 * The stream is drained for the whole relay session. During an active STT turn, fresh frames are
 * synchronously delivered to exactly one [Capture]. Between turns they are intentionally discarded
 * so buffered IVR/TTS tail audio cannot become the beginning of the next transcript.
 */
internal class ChatRelayDownlinkPump(
    private val input: InputStream,
    private val frameBytes: Int,
) : AutoCloseable {
    interface Capture {
        /** Return true to keep capturing, false to complete after this frame. */
        fun onFrame(bytes: ByteArray, length: Int): Boolean
        fun onFinished()
        fun onError(reason: String)
    }

    private val lock = Any()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val discardedBytes = AtomicLong(0L)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ChatRelayDownlinkPump").apply { isDaemon = true }
    }
    private var activeCapture: Capture? = null

    init {
        require(frameBytes > 0) { "relay_downlink_frame_bytes_must_be_positive" }
    }

    fun start() {
        check(!closed.get()) { "relay_downlink_pump_closed" }
        check(started.compareAndSet(false, true)) { "relay_downlink_pump_already_started" }
        executor.execute(::pumpLoop)
    }

    fun beginCapture(capture: Capture) {
        synchronized(lock) {
            check(started.get() && !closed.get()) { "relay_downlink_pump_not_running" }
            check(activeCapture == null) { "relay_downlink_capture_already_active" }
            activeCapture = capture
        }
    }

    fun discardedBytes(): Long = discardedBytes.get()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val capture = synchronized(lock) {
            val current = activeCapture
            activeCapture = null
            current
        }
        try { input.close() } catch (_: Throwable) {}
        executor.shutdownNow()
        try {
            executor.awaitTermination(250, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (capture != null) {
            try { capture.onError("downlink_pump_closed") } catch (_: Throwable) {}
        }
    }

    private fun pumpLoop() {
        val buffer = ByteArray(frameBytes)
        try {
            while (!closed.get()) {
                val read = input.read(buffer, 0, buffer.size)
                if (read < 0) {
                    failActiveCapture("downlink_eof")
                    return
                }
                if (read == 0) continue

                val capture = synchronized(lock) { activeCapture }
                if (capture == null) {
                    discardedBytes.addAndGet(read.toLong())
                    continue
                }

                val keepCapturing = try {
                    capture.onFrame(buffer, read)
                } catch (error: Throwable) {
                    finishCaptureWithError(capture, "capture_${error.javaClass.simpleName}")
                    continue
                }
                if (!keepCapturing) finishCapture(capture)
            }
        } catch (error: Throwable) {
            if (!closed.get()) failActiveCapture("downlink_${error.javaClass.simpleName}")
        }
    }

    private fun finishCapture(capture: Capture) {
        val ownsCapture = synchronized(lock) {
            if (activeCapture !== capture) {
                false
            } else {
                activeCapture = null
                true
            }
        }
        if (ownsCapture) capture.onFinished()
    }

    private fun finishCaptureWithError(capture: Capture, reason: String) {
        val ownsCapture = synchronized(lock) {
            if (activeCapture !== capture) {
                false
            } else {
                activeCapture = null
                true
            }
        }
        if (ownsCapture) {
            try { capture.onError(reason) } catch (_: Throwable) {}
        }
    }

    private fun failActiveCapture(reason: String) {
        val capture = synchronized(lock) {
            val current = activeCapture
            activeCapture = null
            current
        }
        if (capture != null) {
            try { capture.onError(reason) } catch (_: Throwable) {}
        }
    }
}
