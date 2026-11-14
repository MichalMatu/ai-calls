package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.*
import org.junit.Test

class QueuedGemmaAudioInputTest {
    @Test fun cancellationImmediatelyWipesQueuedAudio() {
        val slot = QueuedGemmaAudioInput()
        val audio = ByteArray(960_000) { 0x51.toByte() }
        slot.replace(audio)
        slot.clear()
        assertTrue(audio.all { it == 0.toByte() })
    }
    @Test fun supersedingAudioAndSwitchingToTextWipesPriorQueue() {
        val slot = QueuedGemmaAudioInput()
        val first = ByteArray(32_000) { 0x33.toByte() }
        val next = ByteArray(64_000) { 0x66.toByte() }
        slot.replace(first)
        slot.replace(next)
        assertTrue(first.all { it == 0.toByte() })
        assertTrue(next.all { it == 0x66.toByte() })
        slot.replace(null)
        assertTrue(next.all { it == 0.toByte() })
    }
    @Test fun dispatchedAudioRemainsWorkerOwnedUntilNativeFinally() {
        val slot = QueuedGemmaAudioInput()
        val sending = ByteArray(16_000) { 0x27.toByte() }
        slot.replace(sending)
        slot.detachIfSame(sending)
        slot.clear()
        assertTrue(sending.all { it == 0x27.toByte() })
        sending.fill(0)
        assertTrue(sending.all { it == 0.toByte() })
    }
    @Test fun staleWorkerCannotDetachNewQueue() {
        val slot = QueuedGemmaAudioInput()
        val stale = ByteArray(16_000) { 0x22.toByte() }
        val current = ByteArray(32_000) { 0x44.toByte() }
        slot.replace(stale)
        slot.replace(current)
        slot.detachIfSame(stale)
        assertTrue(current.all { it == 0x44.toByte() })
        slot.clear()
        assertTrue(current.all { it == 0.toByte() })
    }
}
