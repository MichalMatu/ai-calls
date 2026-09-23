package pl.michalmatu.aicallbridge.textagent

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4ModelAcquisitionCatalogTest {
    @Test
    fun `reviewed source is immutable public LiteRT Community artifact bound to production identity`() {
        val source = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT
        val model = Gemma4ModelCatalog.GEMMA_4_E2B_IT

        assertEquals("litert-community/gemma-4-E2B-it-litert-lm", source.repositoryId)
        assertEquals("6e5c4f1e395deb959c494953478fa5cec4b8008f", source.revision)
        assertEquals(model.fileName, source.fileName)
        assertEquals(model.sha256, source.expectedSha256)
        assertEquals(model.expectedBytes, source.expectedBytes)
        assertEquals("apache-2.0", source.declaredLicense)
        assertFalse(source.requiresAuthentication)

        val uri = URI(source.downloadUrl)
        assertEquals("https", uri.scheme)
        assertEquals("huggingface.co", uri.host)
        assertTrue(uri.path.contains("/${source.repositoryId}/resolve/${source.revision}/${source.fileName}"))
        assertFalse(source.downloadUrl.contains("main"))
    }
}
