package pl.michalmatu.aicallbridge.textagent

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Gemma4ModelReadinessProbeTest {
    @Test
    fun `missing active model is missing`() {
        val directory = Files.createTempDirectory("gemma-readiness-missing").toFile()
        val spec = testSpec(expectedBytes = 4)

        val readiness = Gemma4ModelReadinessProbe(directory, spec).check()

        assertEquals(Gemma4ModelReadinessState.MISSING, readiness.state)
        assertEquals("model_missing", readiness.reason)
    }

    @Test
    fun `wrong sized active model is invalid`() {
        val directory = Files.createTempDirectory("gemma-readiness-invalid").toFile()
        val spec = testSpec(expectedBytes = 4)
        val active = directory.resolve(spec.fileName).apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val readiness = Gemma4ModelReadinessProbe(directory, spec).check()

        assertEquals(Gemma4ModelReadinessState.INVALID, readiness.state)
        assertEquals("model_size_mismatch", readiness.reason)
        assertSame(active, readiness.file)
    }

    @Test
    fun `expected active model metadata is ready without hashing file`() {
        val directory = Files.createTempDirectory("gemma-readiness-ready").toFile()
        val spec = testSpec(expectedBytes = 4)
        val active = directory.resolve(spec.fileName).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val readiness = Gemma4ModelReadinessProbe(directory, spec).check()

        assertEquals(Gemma4ModelReadinessState.READY, readiness.state)
        assertEquals(null, readiness.reason)
        assertSame(active, readiness.file)
    }

    private fun testSpec(expectedBytes: Long) = Gemma4ModelSpec(
        modelId = "test",
        fileName = "test.litertlm",
        sha256 = "0".repeat(64),
        expectedBytes = expectedBytes,
    )
}
