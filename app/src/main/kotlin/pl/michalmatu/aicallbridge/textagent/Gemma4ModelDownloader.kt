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
}

/**
 * Streams candidate model bytes from one reviewed source into the existing verified installer.
 *
 * Network transport is deliberately not activation authority: HTTP metadata can reject obviously
 * wrong responses early, but only [Gemma4ModelInstaller] may accept the pinned size/SHA-256 and
 * atomically replace the active model.
 */
internal class Gemma4ModelDownloader(
    private val source: Gemma4ModelAcquisitionSource = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT,
    private val installer: Gemma4ModelInstaller,
    private val transport: Gemma4ModelDownloadTransport = OkHttpGemma4ModelDownloadTransport(),
) {
    fun download(): Gemma4ModelInstallResult {
        if (source.requiresAuthentication) {
            return Gemma4ModelInstallResult.Failure("model_download_auth_not_supported")
        }
        return try {
            transport.open(source).use { response ->
                if (response.statusCode !in 200..299) {
                    return Gemma4ModelInstallResult.Failure(
                        "model_download_http_${response.statusCode}",
                    )
                }

                val expectedBytes = source.expectedBytes
                val contentLength = response.contentLength
                if (expectedBytes != null && contentLength != null && contentLength != expectedBytes) {
                    return Gemma4ModelInstallResult.Failure("model_download_size_mismatch")
                }

                val input = response.body
                    ?: return Gemma4ModelInstallResult.Failure("model_download_body_missing")
                installer.install(input)
            }
        } catch (error: Throwable) {
            Gemma4ModelInstallResult.Failure(
                "model_download_failed_${error.javaClass.simpleName.ifBlank { "Throwable" }}",
            )
        }
    }
}

internal class OkHttpGemma4ModelDownloadTransport(
    private val callFactory: Call.Factory = defaultClient(),
) : Gemma4ModelDownloadTransport {
    override fun open(source: Gemma4ModelAcquisitionSource): Gemma4ModelDownloadResponse {
        require(!source.requiresAuthentication) { "authenticated_model_source_not_supported" }
        val response = callFactory.newCall(buildRequest(source)).execute()
        val finalUrl = response.request.url
        if (
            finalUrl.scheme != "https" ||
            !isTrustedDownloadHost(finalUrl.host)
        ) {
            response.close()
            throw IOException("model_download_untrusted_redirect")
        }
        return OkHttpDownloadResponse(response)
    }

    private class OkHttpDownloadResponse(
        private val response: Response,
    ) : Gemma4ModelDownloadResponse {
        override val statusCode: Int
            get() = response.code

        override val contentLength: Long?
            get() = response.body.contentLength().takeIf { it >= 0L }

        override val body: InputStream?
            get() = response.body.byteStream()

        override fun close() = response.close()
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
            host == "huggingface.co" ||
                host.endsWith(".huggingface.co") ||
                host.endsWith(".hf.co")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
