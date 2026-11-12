package pl.michalmatu.aicallbridge.ui

/** Presentation of the existing off-call protocol; never grants authority or starts media. */
internal data class ShizukuProbePresentation(
    val outcome: Outcome,
    val connection: Boolean?,
    val privileges: Boolean?,
    val lifecycle: Boolean?,
) {
    enum class Outcome { PASSED, FAILED, INCOMPLETE }

    companion object {
        fun parse(report: String): ShizukuProbePresentation {
            val lines = report.lineSequence().filter { '=' in it }.toList()
            val fields = lines.associate { it.substringBefore('=') to it.substringAfter('=') }
            val duplicate = lines.map { it.substringBefore('=') }.let { it.size != it.toSet().size }
            fun bool(key: String): Boolean? = when (fields[key]) {
                "true" -> true
                "false" -> false
                else -> null
            }
            val connection = bool("binder_ping")
            val privileges = fields["service_uid"]?.toIntOrNull()?.let { it == 0 || it == 2000 }
            val expected = mapOf("prepared_before" to false, "active_before" to false,
                "prepared_after_prepare" to true, "active_after_prepare" to false,
                "heartbeat_after_prepare" to false, "prepared_after_abort" to false,
                "active_after_abort" to false, "heartbeat_after_abort" to false)
            val lifecycle = when {
                expected.any { (key, value) -> bool(key)?.let { it != value } == true } -> false
                expected.all { (key, value) -> bool(key) == value } -> true
                else -> null
            }
            val recognized = !duplicate && fields["probe"] == "shizuku-user-service-v1" &&
                fields["mode"] == "offcall-prepare-abort"
            val outcome = when {
                fields.containsKey("error") -> Outcome.FAILED
                !recognized -> Outcome.INCOMPLETE
                connection == false || privileges == false || lifecycle == false ||
                    bool("privileged_uid") == false || bool("offcall_parity_ok") == false -> Outcome.FAILED
                connection == true && privileges == true && lifecycle == true &&
                    bool("privileged_uid") == true && bool("offcall_parity_ok") == true -> Outcome.PASSED
                else -> Outcome.INCOMPLETE
            }
            return ShizukuProbePresentation(outcome, connection, privileges, lifecycle)
        }
    }
}
