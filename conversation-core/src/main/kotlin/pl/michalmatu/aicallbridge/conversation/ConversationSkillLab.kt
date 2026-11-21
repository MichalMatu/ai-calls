package pl.michalmatu.aicallbridge.conversation

data class SkillLabObservation(
    val story: String,
    val index: Int,
    val actualEnd: Boolean,
    val reply: String,
    val elapsedMs: Long,
)
