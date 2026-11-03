package pl.michalmatu.aicallbridge.developerrelay

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallDiagnosticEvidenceTest {
    @Test fun rawErrorOrModelProseCannotBecomeTechnicalCode() {
        assertEquals("recognition_error_10", CallDiagnosticEvidence.safeCode("recognition_error_10"))
        assertEquals("redacted", CallDiagnosticEvidence.safeCode("Numer telefonu 510 100 100"))
        assertEquals("redacted", CallDiagnosticEvidence.safeCode("key=value"))
        assertEquals("redacted", CallDiagnosticEvidence.safeCode("private\ntranscript"))
        assertEquals("redacted", CallDiagnosticEvidence.safeCode("X".repeat(90)))
    }

    @Test fun captureAndReportThreadsCanUpdateWhileSnapshotsAreRendered() {
        val log = CallDiagnosticEvidence(listOf("probe=chat_relay_live_call"))
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(8)
        try {
            repeat(8) { worker ->
                pool.execute {
                    start.await()
                    repeat(70) { index ->
                        log += "worker_${worker}_event_${index}=true"
                        log.render()
                    }
                    finished.countDown()
                }
            }
            start.countDown()
            assertTrue(finished.await(15, TimeUnit.SECONDS))
            val report = log.render()
            assertEquals(561, report.lineSequence().count { it.isNotEmpty() })
            assertTrue(report.startsWith("probe=chat_relay_live_call\n"))
            assertTrue(report.endsWith("\n"))
            assertFalse(report.contains("transcript="))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun evidencePreservesEventsAndSanitizesOversizedTechnicalLine() {
        val log = CallDiagnosticEvidence(emptyList())
        repeat(1100) { log += "event_${it}=true" }
        assertEquals(1100, log.render().lineSequence().count { it.isNotEmpty() })
        val longLine = CallDiagnosticEvidence(emptyList())
        longLine += "status=" + "a".repeat(500)
        assertEquals(256, longLine.render().trimEnd('\n').length)
    }
}
