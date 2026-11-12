package pl.michalmatu.aicallbridge.campaign

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Encrypted private UI draft; not authority. Never put its contents into savedInstanceState. */
internal data class PhoneTaskDraft(
    val id: String = UUID.randomUUID().toString(),
    val step: Int = 0,
    val scenarioId: String = "quote",
    val personalScenarioId: String? = null,
    val title: String = "",
    val goal: String = "",
    val fields: Map<String, String> = emptyMap(),
    val suppliedBrief: String = "",
    val recipients: String = "",
    val subscriptionId: Int? = null,
    val accountScope: String? = null,
    val scheduled: Boolean = false,
    val startLocal: String = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString(),
    val endLocal: String = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0).toString(),
    val zoneId: String = ZoneId.systemDefault().id,
    val retry: Boolean = true,
    val retryMinutes: String = "5",
) {
    override fun toString() = "PhoneTaskDraft(id=$id,step=$step,content=REDACTED)"
}
