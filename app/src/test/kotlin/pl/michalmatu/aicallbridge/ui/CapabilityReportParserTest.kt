package pl.michalmatu.aicallbridge.ui

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.ui.CapabilityReportParser.AudioSourceState

class CapabilityReportParserTest {
    @Test fun valuesKeepEqualsAndDeviceDirectionAndIdentity() {
        val raw = "id=2,type=BUILTIN_SPEAKER(2),dir=sink,product=Phone,rates=48000,channels=2"
        val values = CapabilityReportParser.read("audio_device[0]=$raw\nbuild_fingerprint=vendor=key\n")
        assertEquals("vendor=key", values["build_fingerprint"])
        assertEquals(CapabilityReportParser.AudioDevice("2", "BUILTIN_SPEAKER", "sink", "Phone"),
            CapabilityReportParser.device(values.getValue("audio_device[0]")))
    }

    @Test fun initializationNeverClaimsAudioWasRecorded() {
        assertEquals(AudioSourceState.INITIALIZED,
            CapabilityReportParser.audioSource("result:initialized,state:1,session:9,buffer:4096"))
        assertEquals(AudioSourceState.UNINITIALIZED,
            CapabilityReportParser.audioSource("result:uninitialized,state:0"))
    }

    @Test fun failedSourcesKeepTheirFullEvidenceForDetails() {
        for (error in listOf("UnsupportedOperationException", "SecurityException", "IllegalArgumentException")) {
            val raw = "error:$error:native detail=42"
            val parsed = CapabilityReportParser.read("audio_source_VOICE_CALL=$raw\n")
            assertEquals(raw, parsed["audio_source_VOICE_CALL"])
            assertEquals(AudioSourceState.ERROR, CapabilityReportParser.audioSource(raw))
        }
        assertEquals(AudioSourceState.UNKNOWN, CapabilityReportParser.audioSource(null))
        assertEquals(AudioSourceState.UNKNOWN, CapabilityReportParser.audioSource("future:value"))
    }

    @Test fun separateInputAndOutputDevicesRemainDistinct() {
        val input = CapabilityReportParser.device("id=1,type=TELEPHONY(18),dir=source,product=Phone,rates=,channels=")
        val output = CapabilityReportParser.device("id=2,type=TELEPHONY(18),dir=sink,product=Phone,rates=,channels=")
        assertEquals(input.type, output.type)
        assertNotEquals(input.id, output.id)
        assertNotEquals(input.direction, output.direction)
        assertEquals("", CapabilityReportParser.device("malformed").type)
    }
}
