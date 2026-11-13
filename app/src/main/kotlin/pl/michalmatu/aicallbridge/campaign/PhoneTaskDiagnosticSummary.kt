package pl.michalmatu.aicallbridge.campaign

/** Technical evidence only. A historical report cannot certify a newer attempt. */
internal object PhoneTaskDiagnosticSummary {
    fun snapshot(task: PhoneTask, active: Boolean, report: String?): Map<String, Any> {
        val latest = task.recipients.flatMap { it.attempts }.maxByOrNull { it.startedAt }
        val values = report.orEmpty().lineSequence().filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val matches = latest?.sessionId != null && values["session_id"] == latest.sessionId
        val evidence = linkedMapOf<String, Any>("report_matches_attempt" to matches)
        if (matches) {
            for (key in listOf("turns_completed", "telephony_rx_pcm_bytes",
                    "telephony_rx_partial_pcm_bytes", "telephony_tx_pcm_bytes")) {
                values[key]?.toLongOrNull()?.takeIf { it >= 0 }?.let { evidence[key] = it }
            }
            values["conversation_end_reason"]?.takeIf {
                it.matches(Regex("[A-Za-z0-9_-]{1,96}"))
            }?.let { evidence["conversation_end_reason"] = it }
            evidence["probe_complete"] = values["probe_complete"] == "true"
            evidence["approved_reply_count"] = values.count { (key, value) ->
                key.matches(Regex("turn_[0-9]+_approved_chars")) &&
                    (value.toLongOrNull() ?: 0) > 0
            }
            evidence["barge_events"] = report.orEmpty().lineSequence()
                .count { it == "local_rx_during_tx_detected=true" }
        }
        return linkedMapOf(
            "id" to task.id,
            "state" to task.state.name,
            "issue" to (task.issue?.name ?: "NONE"),
            "active" to active,
            "language" to task.effectiveLanguage(),
            "start_at" to task.startAt,
            "end_at" to task.endAt,
            "retry_enabled" to (task.retryDelayMs != null),
            "recipient_count" to task.recipients.size,
            "recipients" to task.recipients.mapIndexed { index, recipient ->
                mapOf(
                    "index" to index,
                    "state" to recipient.state.name,
                    "attempt_count" to recipient.attempts.size,
                    "answer_count" to recipient.attempts.sumOf { it.answers.size },
                    "last_stage" to (recipient.attempts.lastOrNull()?.stage?.name ?: "NONE"),
                    "last_outcome" to (recipient.attempts.lastOrNull()?.outcome?.name ?: "NONE"),
                )
            },
            "evidence" to evidence,
        )
    }
}
