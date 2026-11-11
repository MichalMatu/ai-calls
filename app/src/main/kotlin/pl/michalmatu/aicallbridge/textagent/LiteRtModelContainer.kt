package pl.michalmatu.aicallbridge.textagent

import java.io.File
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** LiteRT-LM v0.19 container prefix; native inference, not this header, proves compatibility. */
internal object LiteRtModelContainer {
    fun hasHeader(file: File): Boolean = runCatching {
        val bytes = ByteArray(32)
        DataInputStream(file.inputStream()).use { it.readFully(bytes) }
        bytes.size == 32 && String(bytes, 0, 8, Charsets.US_ASCII) == "LITERTLM" &&
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(24) in 32..file.length()
    }.getOrDefault(false)
}
