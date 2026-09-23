package pl.michalmatu.aicallbridge.textagent

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal interface Gemma4ModelDownloadResponse : Closeable {
    val statusCode: Int
    val contentLength: Long?
    val body: InputStream?
}

internal fun interface Gemma4ModelDownloadTransport {
    fun open(source: Gemma4ModelAcquisitionSource): Gemma4ModelDownloadResponse
    fun cancel() = Unit
}

internal data class Gemma4ModelDownloadProgress(
    val bytesRead: Long,
    val expectedBytes: Long?,
)

/**
 * Streams candidate model bytes from one reviewed source into the existing verified installer.
 *
 * Network transport is deliberately not activation authority: HTTP metadata can reject obviously
 * wrong responses early, but only [Gemma4ModelInstaller] may accept the pinned size/SHA-256 and
 * atomically replace the active model. Cancellation closes active network work; a partial staging
 * file remains installer-owned and is removed on stream failure. Retry intentionally starts over.
 */
internal class Gemma4ModelDownloader(
    private val source: Gemma4ModelAcquisitionSource = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT,
    private val installer: Gemma4ModelInstaller,
    private val transport: Gemma4ModelDownloadTransport = OkHttpGemma4ModelDownloadTransport(),
) {
    private val lock = Any()
    private var running = false
    private var cancelRequested = false
    private var activeResponse: Gemma4ModelDownloadResponse? = null

    fun download(
        onProgress: (Gemma4ModelDownloadProgress) -> Unit = {},
    ): Gemma4ModelInstallResult {
        synchronized(lock) {
            if (running) return Gemma4ModelInstallResult.Failure("model_download_already_running")
            running = true
            cancelRequested = false
        }

        try {
            if (source.requiresAuthentication) {
                return Gemma4ModelInstallResult.Failure("model_download_auth_not_supported")
            }

            val response = transport.open(source)
            synchronized(lock) {
                if (cancelRequested) {
                    response.close()
                    return Gemma4ModelInstallResult.Failure("model_download_cancelled")
                }
                activeResponse = response
            }

            response.use { opened ->
                if (opened.statusCode !in 200..299) {
                    return Gemma4ModelInstallResult.Failure(
                        "model_download_http_${opened.statusCode}",
                    )
                }

                val expectedBytes = source.expectedBytes
                val contentLength = opened.contentLength
                if (expectedBytes != null && contentLength != null && contentLength != expectedBytes) {
                    return Gemma4ModelInstallResult.Failure("model_download_size_mismatch")
                }

                val input = opened.body
                    ?: return Gemma4ModelInstallResult.Failure("model_download_body_missing")
                val tracked = ProgressInputStream(input) { bytesRead ->
                    onProgress(Gemma4ModelDownloadProgress(bytesRead, expectedBytes))
                }
                val result = installer.install(tracked)
                if (result is Gemma4ModelInstallResult.Success) return result
                return if (isCancelRequested()) {
                    Gemma4ModelInstallResult.Failure("model_download_cancelled")
                } else {
                    result
                }
            }
        } catch (error: Throwable) {
            return if (isCancelRequested()) {
                Gemma4ModelInstallResult.Failure("model_download_cancelled")
            } else {
                Gemma4ModelInstallResult.Failure(
                    "model_download_failed_${error.javaClass.simpleName.ifBlank { "Throwable" }}",
                )
            }
        } finally {
            synchronized(lock) {
                activeResponse = null
                running = false
                cancelRequested = false
            }
        }
    }

    fun cancel(): Boolean {
        val response = synchronized(lock) {
            if (!running) return false
            cancelRequested = true
            activeResponse
        }
        transport.cancel()
        response?.close()
        return true
    }

    private fun isCancelRequested(): Boolean = synchronized(lock) { cancelRequested }

    private class ProgressInputStream(
        private val delegate: InputStream,
        private val onBytesRead: (Long) -> Unit,
    ) : InputStream() {
        private var totalRead = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) report(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) report(read.toLong())
            return read
        }

        override fun close() = delegate.close()

        private fun report(delta: Long) {
            totalRead += delta
            onBytesRead(totalRead)
        }
    }
}

internal class OkHttpGemma4ModelDownloadTransport(
    private val callFactory: Call.Factory = defaultClient(),
) : Gemma4ModelDownloadTransport {
    private val lock = Any()
    private var activeCall: Call? = null

    override fun open(source: Gemma4ModelAcquisitionSource): Gemma4ModelDownloadResponse {
        require(!source.requiresAuthentication) { "authenticated_model_source_not_supported" }
        val call = callFactory.newCall(buildRequest(source))
        synchronized(lock) { activeCall = call }
        val response = try {
            call.execute()
        } catch (error: Throwable) {
            clearActive(call)
            throw error
        }
        val finalUrl = response.request.url
        if (finalUrl.scheme != "https" || !isTrustedDownloadHost(finalUrl.host)) {
            response.close()
            clearActive(call)
            throw IOException("model_download_untrusted_redirect")
        }
        return OkHttpDownloadResponse(response) { clearActive(call) }
    }

    override fun cancel() {
        synchronized(lock) { activeCall }?.cancel()
    }

    private fun clearActive(call: Call) {
        synchronized(lock) {
            if (activeCall === call) activeCall = null
        }
    }

    private class OkHttpDownloadResponse(
        private val response: Response,
        private val onClose: () -> Unit,
    ) : Gemma4ModelDownloadResponse {
        override val statusCode: Int get() = response.code
        override val contentLength: Long? get() = response.body.contentLength().takeIf { it >= 0L }
        override val body: InputStream? get() = response.body.byteStream()
        override fun close() {
            try { response.close() } finally { onClose() }
        }
    }

    internal companion object {
        fun buildRequest(source: Gemma4ModelAcquisitionSource): Request {
            require(!source.requiresAuthentication) { "authenticated_model_source_not_supported" }
            return Request.Builder()
                .url(source.downloadUrl)
                .header("Cache-Control", "no-store")
                .header("User-Agent", "android-ai-call-bridge/0.2")
                .get()
                .build()
        }

        internal fun isTrustedDownloadHost(host: String): Boolean =
            host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
