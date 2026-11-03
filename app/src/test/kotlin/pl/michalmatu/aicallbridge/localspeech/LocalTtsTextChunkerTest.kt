package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTtsTextChunkerTest {
    @Test fun longAuthorizedUtteranceIsPreservedWithoutTruncation() {
        val source = ("Proszę potwierdzić szczegóły długiej rozmowy. ").repeat(500)
        val parts = LocalTtsTextChunker.split(source, 280)
        assertTrue(parts.size > 20)
        assertTrue(parts.all { it.length <= 280 && it.isNotEmpty() })
        assertEquals(source, parts.joinToString(""))
    }

    @Test fun utf16SurrogatePairIsNotBroken() {
        val source = "abcde😀FGHIJ😀koniec"
        val parts = LocalTtsTextChunker.split(source, 6)
        assertEquals(source, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 6 })
        assertTrue(parts.all { part ->
            !Character.isLowSurrogate(part.first()) &&
                !Character.isHighSurrogate(part.last())
        })
    }

    @Test fun shortUtteranceIsOneChunk() {
        assertEquals(listOf("Tak, rozumiem."), LocalTtsTextChunker.split("Tak, rozumiem.", 200))
    }
}
