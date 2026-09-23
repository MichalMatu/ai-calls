package pl.michalmatu.aicallbridge.textagent

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4ModelDownloadLifecycleTest {
    @Test
    fun `operation gate serializes import and download`() {
        val gate = Gemma4ModelOperationGate()

        assertEquals(Gemma4ModelOperationState.IDLE, gate.state())
        assertTrue(gate.tryBeginImport())
        assertEquals(Gemma4ModelOperationState.IMPORTING, gate.state())
        assertFalse(gate.tryBeginDownload())
        assertFalse(gate.tryBeginImport())
        gate.finishImport()

        assertTrue(gate.tryBeginDownload())
        assertEquals(Gemma4ModelOperationState.DOWNLOADING, gate.state())
        assertFalse(gate.tryBeginImport())
        assertFalse(gate.tryBeginDownload())
        gate.finishDownload()
        assertEquals(Gemma4ModelOperationState.IDLE, gate.state())
    }

    @Test
    fun `download reports streamed progress through expected total`() {
        val bytes = ByteArray(3 * 1024 * 1024 + 17) { index -> (index % 251).toByte() }
        val spec = specFor(bytes)
        val directory = Files.createTempDirectory("gemma-progress").toFile()
        val source = sourceFor(spec)
        val response = FakeResponse(200, bytes.size.toLong(), ByteArrayInputStream(bytes))
        val progress = mutableListOf<Gemma4ModelDownloadProgress>()
        val downloader = Gemma4ModelDownloader(
            source = source,
            installer = Gemma4ModelInstaller(directory, spec),
            transport = FakeTransport(response),
        )

        val result = downloader.download { progress += it }

        assertTrue(result is Gemma4ModelInstallResult.Success)
        assertTrue(progress.isNotEmpty())
        assertEquals(bytes.size.toLong(), progress.last().bytesRead)
        assertEquals(bytes.size.toLong(), progress.last().expectedBytes)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesRead >= a.bytesRead })
    }

    @Test
    fun `cancel closes transport and response and leaves no partial activation`() {
        val payload = "never-completes".toByteArray()
        val spec = specFor(payload)
        val directory = Files.createTempDirectory("gemma-cancel").toFile()
        val active = File(directory, spec.fileName).apply { writeText("previous-active") }
        val blocking = BlockingInputStream()
        val response = FakeResponse(200, null, blocking)
        val transport = FakeTransport(response)
        val downloader = Gemma4ModelDownloader(
            source = sourceFor(spec),
            installer = Gemma4ModelInstaller(directory, spec),
            transport = transport,
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Gemma4ModelInstallResult> { downloader.download() }
            assertTrue(blocking.started.await(2, TimeUnit.SECONDS))

            assertTrue(downloader.cancel())
            val result = future.get(2, TimeUnit.SECONDS)

            assertEquals(Gemma4ModelInstallResult.Failure("model_download_cancelled"), result)
            assertEquals(1, transport.cancelCalls)
            assertTrue(response.closed)
            assertEquals("previous-active", active.readText())
            assertFalse(File(directory, ".${spec.fileName}.importing").exists())
            assertFalse(downloader.cancel())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun sourceFor(spec: Gemma4ModelSpec) = Gemma4ModelAcquisitionSource(
        repositoryId = "example/model",
        revision = "0123456789abcdef0123456789abcdef01234567",
        fileName = spec.fileName,
        expectedSha256 = spec.sha256,
        expectedBytes = spec.expectedBytes,
        declaredLicense = "test",
        requiresAuthentication = false,
    )

    private fun specFor(bytes: ByteArray) = Gemma4ModelSpec(
        modelId = "test-model",
        fileName = "model.litertlm",
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        expectedBytes = bytes.size.toLong(),
    )

    private class BlockingInputStream : InputStream() {
        val started = CountDownLatch(1)
        @Volatile private var closed = false
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            started.countDown()
            while (!closed) Thread.sleep(5)
            throw IOException("closed")
        }
        override fun close() { closed = true }
    }

    private class FakeResponse(
        override val statusCode: Int,
        override val contentLength: Long?,
        override val body: InputStream?,
    ) : Gemma4ModelDownloadResponse {
        @Volatile var closed = false
        override fun close() { closed = true; body?.close() }
    }

    private class FakeTransport(
        private val response: Gemma4ModelDownloadResponse,
    ) : Gemma4ModelDownloadTransport {
        var cancelCalls = 0
        override fun open(source: Gemma4ModelAcquisitionSource): Gemma4ModelDownloadResponse = response
        override fun cancel() { cancelCalls += 1 }
    }
}
