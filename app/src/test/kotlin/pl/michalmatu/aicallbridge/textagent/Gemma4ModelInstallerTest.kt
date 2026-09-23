package pl.michalmatu.aicallbridge.textagent

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4ModelInstallerTest {
    @Test
    fun `verified bytes are atomically activated at app owned model path`() {
        val directory = Files.createTempDirectory("gemma-model-install").toFile()
        val bytes = "verified-gemma-model".toByteArray()
        val spec = specFor(bytes)
        val installer = Gemma4ModelInstaller(directory, spec)

        val result = installer.install(ByteArrayInputStream(bytes))

        assertTrue(result is Gemma4ModelInstallResult.Success)
        val success = result as Gemma4ModelInstallResult.Success
        assertEquals(spec.modelId, success.modelId)
        assertEquals(spec.sha256, success.sha256)
        assertEquals(bytes.size.toLong(), success.bytesWritten)
        assertEquals(spec.fileName, success.file.name)
        assertArrayEquals(bytes, success.file.readBytes())
        assertFalse(File(directory, ".${spec.fileName}.importing").exists())
    }

    @Test
    fun `hash mismatch fails closed and preserves previously active model`() {
        val directory = Files.createTempDirectory("gemma-model-mismatch").toFile()
        val expected = "expected-model".toByteArray()
        val previous = "previous-active-model".toByteArray()
        val spec = specFor(expected)
        val active = File(directory, spec.fileName).apply { writeBytes(previous) }
        val installer = Gemma4ModelInstaller(directory, spec)

        val result = installer.install(ByteArrayInputStream("wrong-model".toByteArray()))

        assertEquals(
            Gemma4ModelInstallResult.Failure("model_sha256_mismatch"),
            result,
        )
        assertArrayEquals(previous, active.readBytes())
        assertFalse(File(directory, ".${spec.fileName}.importing").exists())
    }

    @Test
    fun `activation failure preserves previously active model and removes staged bytes`() {
        val directory = Files.createTempDirectory("gemma-model-move-failure").toFile()
        val bytes = "verified-model".toByteArray()
        val previous = "previous-active-model".toByteArray()
        val spec = specFor(bytes)
        val active = File(directory, spec.fileName).apply { writeBytes(previous) }
        val installer = Gemma4ModelInstaller(
            directory = directory,
            spec = spec,
            atomicMove = { _, _ -> throw IllegalStateException("move failed") },
        )

        val result = installer.install(ByteArrayInputStream(bytes))

        assertEquals(
            Gemma4ModelInstallResult.Failure("model_activation_failed_IllegalStateException"),
            result,
        )
        assertArrayEquals(previous, active.readBytes())
        assertFalse(File(directory, ".${spec.fileName}.importing").exists())
    }

    @Test
    fun `production catalog pins exact Gemma 4 E2B identity`() {
        val spec = Gemma4ModelCatalog.GEMMA_4_E2B_IT

        assertEquals("Gemma 4 E2B IT", spec.modelId)
        assertEquals(Gemma4LiteRtTextBackendFactory.MODEL_FILE_NAME, spec.fileName)
        assertEquals(
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            spec.sha256,
        )
    }

    private fun specFor(bytes: ByteArray): Gemma4ModelSpec = Gemma4ModelSpec(
        modelId = "test-model",
        fileName = "gemma-test.litertlm",
        sha256 = sha256(bytes),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
