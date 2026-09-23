package pl.michalmatu.aicallbridge.textagent

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4ModelDownloaderTest {
    @Test
    fun `verified response streams through existing installer and atomically activates`() {
        val bytes = "downloaded-verified-model".toByteArray()
        val spec = specFor(bytes)
        val directory = Files.createTempDirectory("gemma-download").toFile()
        val source = sourceFor(spec)
        val response = FakeDownloadResponse(200, bytes.size.toLong(), CountingInputStream(bytes))
        val transport = FakeTransport(response)
        val downloader = Gemma4ModelDownloader(
            source = source,
            installer = Gemma4ModelInstaller(directory, spec),
            transport = transport,
        )

        val result = downloader.download()

        assertTrue(result is Gemma4ModelInstallResult.Success)
        assertArrayEquals(bytes, File(directory, spec.fileName).readBytes())
        assertEquals(source, transport.openedSource)
        assertTrue(response.closed)
        assertFalse(File(directory, ".${spec.fileName}.importing").exists())
    }

    @Test
    fun `declared content length mismatch fails before consuming body`() {
        val bytes = "expected-model".toByteArray()
        val spec = specFor(bytes)
        val directory = Files.createTempDirectory("gemma-download-size").toFile()
        val active = File(directory, spec.fileName).apply { writeText("previous") }
        val counting = CountingInputStream(bytes)
        val response = FakeDownloadResponse(200, bytes.size.toLong() + 1L, counting)
        val downloader = Gemma4ModelDownloader(
            source = sourceFor(spec),
            installer = Gemma4ModelInstaller(directory, spec),
            transport = FakeTransport(response),
        )

        val result = downloader.download()

        assertEquals(Gemma4ModelInstallResult.Failure("model_download_size_mismatch"), result)
        assertEquals(0, counting.readCalls)
        assertEquals("previous", active.readText())
        assertTrue(response.closed)
    }

    @Test
    fun `http failure does not consume or activate body`() {
        val bytes = "expected-model".toByteArray()
        val spec = specFor(bytes)
        val directory = Files.createTempDirectory("gemma-download-http").toFile()
        val counting = CountingInputStream(bytes)
        val response = FakeDownloadResponse(503, bytes.size.toLong(), counting)
        val downloader = Gemma4ModelDownloader(
            source = sourceFor(spec),
            installer = Gemma4ModelInstaller(directory, spec),
            transport = FakeTransport(response),
        )

        val result = downloader.download()

        assertEquals(Gemma4ModelInstallResult.Failure("model_download_http_503"), result)
        assertEquals(0, counting.readCalls)
        assertFalse(File(directory, spec.fileName).exists())
        assertTrue(response.closed)
    }

    @Test
    fun `transport exception fails closed`() {
        val bytes = "expected-model".toByteArray()
        val spec = specFor(bytes)
        val directory = Files.createTempDirectory("gemma-download-network").toFile()
        val downloader = Gemma4ModelDownloader(
            source = sourceFor(spec),
            installer = Gemma4ModelInstaller(directory, spec),
            transport = Gemma4ModelDownloadTransport { throw IOException("offline") },
        )

        assertEquals(
            Gemma4ModelInstallResult.Failure("model_download_failed_IOException"),
            downloader.download(),
        )
        assertFalse(File(directory, spec.fileName).exists())
    }

    @Test
    fun `production request is anonymous immutable https get`() {
        val source = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT
        val request = OkHttpGemma4ModelDownloadTransport.buildRequest(source)

        assertEquals("GET", request.method)
        assertEquals("https", request.url.scheme)
        assertEquals("huggingface.co", request.url.host)
        assertTrue(request.url.encodedPath.contains(source.revision))
        assertNull(request.header("Authorization"))
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

    private class CountingInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var readCalls = 0
        override fun read(): Int { readCalls += 1; return delegate.read() }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCalls += 1
            return delegate.read(buffer, offset, length)
        }
    }

    private class FakeDownloadResponse(
        override val statusCode: Int,
        override val contentLength: Long?,
        override val body: InputStream?,
    ) : Gemma4ModelDownloadResponse {
        var closed = false
        override fun close() { closed = true; body?.close() }
    }

    private class FakeTransport(
        private val response: Gemma4ModelDownloadResponse,
    ) : Gemma4ModelDownloadTransport {
        var openedSource: Gemma4ModelAcquisitionSource? = null
        override fun open(source: Gemma4ModelAcquisitionSource): Gemma4ModelDownloadResponse {
            openedSource = source
            return response
        }
    }
}
