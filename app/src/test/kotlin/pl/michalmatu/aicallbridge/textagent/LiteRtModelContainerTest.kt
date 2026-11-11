package pl.michalmatu.aicallbridge.textagent

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.ModelCompatibilityStamp

class LiteRtModelContainerTest {
    @Test fun changingOnlyTheExtensionDoesNotCreateAModelContainer() {
        val file = Files.createTempFile("synthetic-model-", ".litertlm").toFile()
        try {
            file.writeText("This is not a model")
            assertFalse(LiteRtModelContainer.hasHeader(file))
            file.writeBytes(byteArrayOf())
            assertFalse(LiteRtModelContainer.hasHeader(file))
        } finally { file.delete() }
    }
    @Test fun truncatedAndOutOfBoundsHeadersAreRejectedBeforeNativeAllocation() {
        val file = Files.createTempFile("synthetic-model-", ".litertlm").toFile()
        try {
            for (end in listOf(-1L, 0L, 31L, 65L)) {
                file.writeBytes(header(end))
                assertFalse(LiteRtModelContainer.hasHeader(file))
            }
            file.writeBytes(header(64).copyOf(31))
            assertFalse(LiteRtModelContainer.hasHeader(file))
            file.writeBytes(header(64))
            assertTrue(LiteRtModelContainer.hasHeader(file))
        } finally { file.delete() }
    }
    @Test fun nativeCompatibilityEvidenceExpiresWhenTheFileChanges() {
        val file = Files.createTempFile("synthetic-model-", ".litertlm").toFile()
        try {
            assertNull(ModelCompatibilityStamp.of(file))
            file.writeBytes(header(64))
            val first = ModelCompatibilityStamp.of(file)
            assertNotNull(first)
            assertTrue(file.setLastModified(file.lastModified() + 2_000))
            assertNotEquals(first, ModelCompatibilityStamp.of(file))
            file.delete()
            assertNull(ModelCompatibilityStamp.of(file))
        } finally { file.delete() }
    }
    private fun header(end: Long): ByteArray = ByteArray(64).also { bytes ->
        "LITERTLM".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(24, end)
    }
}
