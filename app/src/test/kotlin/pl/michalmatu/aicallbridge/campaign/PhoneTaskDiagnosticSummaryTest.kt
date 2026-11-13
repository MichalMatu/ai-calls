package pl.michalmatu.aicallbridge.campaign

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PhoneTaskDiagnosticSummaryTest {
    private fun task() = PhoneTask(
        UUID.randomUUID().toString(), "PRIVATE_TITLE", "quote", "PRIVATE_GOAL",
        "PRIVATE_BRIEF", 1, "PRIVATE_SIM", 1_000, 100_000, "Europe/Warsaw",
        null, 1, listOf(PhoneTaskRecipient(UUID.randomUUID().toString(),
            "PRIVATE_NAME", "+12025550101", attempts = listOf(
                PhoneTaskAttempt(UUID.randomUUID().toString(), 1_000,
                    sessionId = "current-session",
                    answers = listOf(PhoneTaskAnswer(1, "PRIVATE_TRANSCRIPT")))))))

    @Test fun historicalOrUnboundReportCannotCertifyCurrentTask() {
        val task = task()
        val evidence = PhoneTaskDiagnosticSummary.snapshot(task, false,
            "session_id=old-session\ntelephony_tx_pcm_bytes=900\nprobe_complete=true\n")["evidence"] as Map<*, *>
        assertEquals(mapOf("report_matches_attempt" to false), evidence)
        val preparing = task.copy(recipients = task.recipients.map { recipient ->
            recipient.copy(attempts = recipient.attempts + PhoneTaskAttempt(
                UUID.randomUUID().toString(), 2_000))
        })
        val newer = PhoneTaskDiagnosticSummary.snapshot(preparing, true,
            "session_id=current-session\nprobe_complete=true\n")["evidence"] as Map<*, *>
        assertEquals(mapOf("report_matches_attempt" to false), newer)
    }

    @Test fun matchedReportReturnsOnlyTechnicalCountersAndSanitizedCodes() {
        val summary = PhoneTaskDiagnosticSummary.snapshot(task(), false,
            "session_id=current-session\ntelephony_tx_pcm_bytes=120\n" +
                "turns_completed=2\nturn_0_approved_chars=900\nturn_1_approved_chars=20\nturn_2_approved_chars=0\n" +
                "conversation_end_reason=operator_stopped\nprobe_complete=true\n" +
                "local_rx_during_tx_detected=true\nprivate_text=PRIVATE_REPORT\n")
        val evidence = summary["evidence"] as Map<*, *>
        assertEquals(true, evidence["report_matches_attempt"])
        assertEquals(120L, evidence["telephony_tx_pcm_bytes"])
        assertEquals(1, evidence["approved_reply_count"])
        assertEquals(1, evidence["barge_events"])
        assertEquals("operator_stopped", evidence["conversation_end_reason"])
        assertFalse(summary.toString().contains("PRIVATE_"))
        assertFalse(summary.toString().contains("+12025550101"))
        assertFalse(summary.toString().contains("current-session"))
    }

    @Test fun malformedMetricsAndPrivateTerminationTextStayOutOfSnapshot() {
        val evidence = PhoneTaskDiagnosticSummary.snapshot(task(), false,
            "session_id=current-session\ntelephony_tx_pcm_bytes=-1\n" +
                "turns_completed=99999999999999999999999\n" +
                "conversation_end_reason=PRIVATE TEXT\n")["evidence"] as Map<*, *>
        assertFalse(evidence.containsKey("telephony_tx_pcm_bytes"))
        assertFalse(evidence.containsKey("turns_completed"))
        assertFalse(evidence.containsKey("conversation_end_reason"))
    }
}
