package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gemma4ModelDownloadPresentationTest {
    @Test
    fun `confirmation exposes reviewed source license size verification and restart policy`() {
        val source = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT
        val presentation = Gemma4ModelDownloadPresentation(source)

        assertEquals("Download reviewed Gemma 4 model (~2.59 GB)", presentation.startButtonLabel)
        assertEquals("Download 2.59 GB", presentation.confirmButtonLabel)
        assertEquals("Cancel", presentation.cancelButtonLabel)
        assertTrue(presentation.sourceSummary.contains(source.repositoryId))
        assertTrue(presentation.sourceSummary.contains("Apache-2.0"))
        assertTrue(presentation.sourceSummary.contains("2.59 GB"))
        assertTrue(presentation.confirmationMessage.contains(source.revision))
        assertTrue(presentation.confirmationMessage.contains("2,588,147,712 bytes"))
        assertTrue(presentation.confirmationMessage.contains("SHA-256"))
        assertTrue(presentation.confirmationMessage.contains("Retry starts from byte 0"))
        assertTrue(presentation.confirmationMessage.contains("Wi-Fi"))
    }

    @Test
    fun `progress text is bounded and shows percentage when total is known`() {
        val source = Gemma4ModelAcquisitionCatalog.GEMMA_4_E2B_IT
        val presentation = Gemma4ModelDownloadPresentation(source)

        assertEquals(
            "Downloading Gemma 4: 25% (647.0 MB / 2588.1 MB)",
            presentation.progressText(Gemma4ModelDownloadProgress(647_036_928L, 2_588_147_712L)),
        )
    }
}
