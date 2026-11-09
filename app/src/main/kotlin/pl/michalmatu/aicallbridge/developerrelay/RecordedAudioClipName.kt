package pl.michalmatu.aicallbridge.developerrelay

/** Canonical app-private filenames for consecutive recorded RX windows. */
internal object RecordedAudioClipName {
    private val pattern = Regex("turn-([0-9]+)-(rx|tx|tx-ack)(?:-part-([0-9]+))?[.]wav")

    fun create(turn: Int, channel: String, part: Int = 1): String {
        require(turn >= 0 && part >= 1)
        require(channel == "rx" || channel == "tx" || channel == "tx-ack")
        val base = "turn-" + turn.toString().padStart(2, '0') + "-" + channel
        return if (part == 1) "$base.wav"
            else base + "-part-" + part.toString().padStart(3, '0') + ".wav"
    }

    fun isSafe(name: String): Boolean {
        val match = pattern.matchEntire(name) ?: return false
        val turn = match.groupValues[1].toIntOrNull() ?: return false
        val channel = match.groupValues[2]
        val part = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
        return runCatching { create(turn, channel, part) == name }.getOrDefault(false)
    }

    fun matches(name: String, turn: Int, channel: String): Boolean {
        if (!isSafe(name)) return false
        val match = pattern.matchEntire(name) ?: return false
        return match.groupValues[1].toIntOrNull() == turn && match.groupValues[2] == channel
    }
}
