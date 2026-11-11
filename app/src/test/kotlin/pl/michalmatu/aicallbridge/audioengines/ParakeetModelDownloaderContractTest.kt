package pl.michalmatu.aicallbridge.audioengines

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ParakeetModelDownloaderContractTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingTruncatedAndOversizedFilesNeverAppearVerified() {
        val file = File(temporary.root, ParakeetModelFile.NAME)
        assertEquals(ParakeetModelIntegrity.MISSING, ParakeetModelFile.integrity(file))
        file.writeText("incomplete")
        assertEquals(ParakeetModelIntegrity.INCOMPLETE, ParakeetModelFile.integrity(file))
        assertFalse(ParakeetModelFile.validPinnedModel(file))
        RandomAccessFile(file, "rw").use { it.setLength(ParakeetModelFile.MIN_SIZE_BYTES + 1) }
        assertEquals(ParakeetModelIntegrity.INCOMPLETE, ParakeetModelFile.integrity(file))
        assertFalse(ParakeetModelFile.validPinnedModel(file))
    }

    @Test fun matchingSizeDoesNotImplyIntegrityAndChangedMetadataInvalidatesCachedFailure() {
        val file = File(temporary.root, ParakeetModelFile.NAME)
        RandomAccessFile(file, "rw").use { it.setLength(ParakeetModelFile.MIN_SIZE_BYTES) }
        assertEquals(ParakeetModelIntegrity.UNVERIFIED, ParakeetModelFile.integrity(file))
        assertFalse(ParakeetModelFile.validPinnedModel(file))
        assertEquals(ParakeetModelIntegrity.INVALID, ParakeetModelFile.integrity(file))
        assertTrue(file.setLastModified(file.lastModified() + 2000))
        assertEquals(ParakeetModelIntegrity.UNVERIFIED, ParakeetModelFile.integrity(file))
    }

    @Test fun modelIdentityPinnedToExactRevisionBytesSha() {
        assertEquals(614588752L, ParakeetModelFile.MIN_SIZE_BYTES)
        assertEquals("1f78e5eb6175b1b2cce35f16bcf9e2027b83e8bd", ParakeetModelFile.HF_REVISION)
        assertEquals("66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504",
            ParakeetModelFile.EXPECTED_SHA256)
        assertTrue(ParakeetModelFile.DOWNLOAD_URL.contains("/resolve/1f78e5eb"))
    }
    @Test fun rejectsUntrustedRedirectHosts() {
        assertTrue(ParakeetModelDownloader.trustedHfHost("huggingface.co"))
        assertTrue(ParakeetModelDownloader.trustedHfHost("cas-bridge.xethub.hf.co"))
        assertFalse(ParakeetModelDownloader.trustedHfHost("attacker.huggingface.co.evil.com"))
        assertFalse(ParakeetModelDownloader.trustedHfHost("example.com"))
    }
}
