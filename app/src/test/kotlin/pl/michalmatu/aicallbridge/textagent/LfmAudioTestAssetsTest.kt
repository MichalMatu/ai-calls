package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.*
import org.junit.Test

class LfmAudioTestAssetsTest {
    @Test fun exactFourPinnedAudioComponentsNeverAcceptArbitraryGguf() {
        val parts = LfmAudioTestAssets.parts
        assertEquals(4, parts.size)
        assertEquals(1074794688L, parts.sumOf { it.bytes })
        assertEquals(4, parts.map { it.name }.toSet().size)
        assertTrue(parts.all { it.name.endsWith(".gguf") &&
            it.sha256.matches(Regex("[0-9a-f]{64}")) && it.bytes > 50000000L })
    }
}
