package pl.michalmatu.aicallbridge.ui

import org.junit.Assert.*
import org.junit.Test

class CapabilityReportParserTest {
    @Test fun valuesKeepEqualsAndParseDevicesWithoutLosingOriginalReport() {
        val values = CapabilityReportParser.read(
            "model=SM-S906B\naudio_device[0]=id=2,type=BUILTIN_SPEAKER(2),dir=sink,product=Phone,rates=48000,channels=2\n" +
            "build_fingerprint=vendor=key\naudio_source_VOICE_CALL=result:initialized,state:1,session:9,buffer:4096\n"
        )
        assertEquals("vendor=key", values["build_fingerprint"])
        assertEquals("initialized", CapabilityReportParser.audioSource(values["audio_source_VOICE_CALL"]))
        assertEquals("BUILTIN SPEAKER", CapabilityReportParser.deviceType(values.getValue("audio_device[0]")))
        assertEquals("Phone", CapabilityReportParser.deviceProduct(values.getValue("audio_device[0]")))
    }

    @Test fun errorsAndAbsentSourcesNeverClaimCaptureWorks() {
        assertEquals("error: SecurityException",
            CapabilityReportParser.audioSource("error:SecurityException:permission missing"))
        assertEquals("uninitialized",
            CapabilityReportParser.audioSource("result:uninitialized,state:0"))
        assertEquals("unknown", CapabilityReportParser.audioSource(null))
    }
}
