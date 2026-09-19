package pl.michalmatu.aicallbridge.developerrelay

import java.io.File
import java.nio.charset.StandardCharsets

/** App-private, network-free mailbox between the Android backend and the host ADB bridge. */
internal class ChatRelayMailbox(
    private val directory: File,
) {
    private val requestFile = File(directory, REQUEST_FILE_NAME)
    private val responseFile = File(directory, RESPONSE_FILE_NAME)

    @Synchronized
    fun publishRequest(envelope: ChatRelayEnvelope) {
        envelope.validate(MAX_REQUEST_CHARS)
        directory.mkdirsOrThrow()
        responseFile.delete()
        atomicWrite(requestFile, ChatRelayEnvelopeCodec.encode("request", envelope))
    }

    @Synchronized
    fun readRequest(): ChatRelayEnvelope? =
        readEnvelope(requestFile, "request")

    @Synchronized
    fun publishResponse(envelope: ChatRelayEnvelope) {
        envelope.validate(MAX_RESPONSE_CHARS)
        directory.mkdirsOrThrow()
        atomicWrite(responseFile, ChatRelayEnvelopeCodec.encode("response", envelope))
    }

    /**
     * Returns and consumes only the exact response expected by the active generation. A stale or
     * malformed response is discarded so it cannot block a later valid host response.
     */
    @Synchronized
    fun takeMatchingResponse(sessionId: String, turnId: Long): ChatRelayEnvelope? {
        if (!responseFile.isFile) return null
        val response = try {
            readEnvelope(responseFile, "response")
        } catch (_: Throwable) {
            responseFile.delete()
            return null
        } ?: return null
        responseFile.delete()
        if (response.sessionId != sessionId || response.turnId != turnId) return null
        response.validate(MAX_RESPONSE_CHARS)
        return response
    }

    @Synchronized
    fun clear() {
        requestFile.delete()
        responseFile.delete()
        directory.listFiles()?.forEach { file ->
            if (file.name.endsWith(TEMP_SUFFIX)) file.delete()
        }
    }

    private fun readEnvelope(file: File, kind: String): ChatRelayEnvelope? {
        if (!file.isFile) return null
        val bytes = file.readBytes()
        require(bytes.size <= MAX_FILE_BYTES) { "relay_mailbox_file_too_large" }
        return ChatRelayEnvelopeCodec.decode(kind, bytes.toString(StandardCharsets.UTF_8))
    }

    private fun atomicWrite(file: File, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_FILE_BYTES) { "relay_mailbox_file_too_large" }
        val temp = File(directory, file.name + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IllegalStateException("relay_mailbox_atomic_publish_failed")
        }
    }

    private fun File.mkdirsOrThrow() {
        if (!exists() && !mkdirs()) throw IllegalStateException("relay_mailbox_directory_create_failed")
        if (!isDirectory) throw IllegalStateException("relay_mailbox_path_not_directory")
    }

    companion object {
        const val DIRECTORY_NAME = "chat-relay"
        const val REQUEST_FILE_NAME = "request.txt"
        const val RESPONSE_FILE_NAME = "response.txt"
        const val MAX_REQUEST_CHARS = 1_500
        const val MAX_RESPONSE_CHARS = 600
        private const val MAX_FILE_BYTES = 16 * 1024
        private const val TEMP_SUFFIX = ".tmp"
    }
}
