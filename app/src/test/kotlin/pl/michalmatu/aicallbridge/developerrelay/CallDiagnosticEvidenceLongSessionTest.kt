package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallDiagnosticEvidenceLongSessionTest {
    @Test fun preservesEveryDiagnosticEventBeyondFormer1024LineLimit() {
        val evidence = CallDiagnosticEvidence(listOf("session_started=true"))
        repeat(1_800) { i -> evidence += "turn_event_$i=true" }
        val lines = evidence.render().trim().lines()
        assertEquals(1_801, lines.size)
        assertTrue(lines.contains("turn_event_1799=true"))
    }
}
