package pl.michalmatu.aicallbridge.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDebugProtocolTest {
    @Test fun onlyExplicitOperationsExist() {
        assertEquals(1, UsbDebugProtocol.VERSION)
        assertTrue(UsbDebugProtocol.accepts("status"))
        assertTrue(UsbDebugProtocol.accepts("profile_set"))
        assertTrue(UsbDebugProtocol.accepts("audio_compat_check"))
        assertTrue(UsbDebugProtocol.accepts("audio_corpus_check"))
        assertTrue(UsbDebugProtocol.accepts("audio_transcript_corpus_check"))
        assertTrue(UsbDebugProtocol.accepts("audio_staged_corpus_check"))
        assertFalse("audio_staged_corpus_check" in UsbDebugProtocol.mutableActions)
        assertFalse("audio_transcript_corpus_check" in UsbDebugProtocol.mutableActions)
        assertFalse("audio_corpus_check" in UsbDebugProtocol.mutableActions)
        assertTrue(UsbDebugProtocol.accepts("grant_revoke"))
        assertFalse(UsbDebugProtocol.accepts("grant_replace"))
        assertFalse(UsbDebugProtocol.accepts("disable_stop"))
        assertFalse(UsbDebugProtocol.accepts("place_call"))
        assertFalse(UsbDebugProtocol.accepts("write_shared_prefs"))
        assertFalse(UsbDebugProtocol.accepts(""))
    }

    @Test fun readOnlyAndMutationCommandsAreSeparate() {
        assertFalse("status" in UsbDebugProtocol.mutableActions)
        assertFalse("evidence_status" in UsbDebugProtocol.mutableActions)
        assertFalse("profile_check" in UsbDebugProtocol.mutableActions)
        assertTrue("audio_compat_check" in UsbDebugProtocol.mutableActions)
        assertTrue(UsbDebugProtocol.mutableActions.all { UsbDebugProtocol.accepts(it) })
        assertTrue(UsbDebugProtocol.needsOfflineVoiceProbe("profile_set"))
        assertTrue(UsbDebugProtocol.needsOfflineVoiceProbe("profile_check"))
        assertFalse(UsbDebugProtocol.needsOfflineVoiceProbe("grant_revoke"))
    }
}
