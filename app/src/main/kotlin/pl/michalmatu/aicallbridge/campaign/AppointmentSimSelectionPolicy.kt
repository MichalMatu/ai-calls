package pl.michalmatu.aicallbridge.campaign

/** Never silently switch from the SIM selected before an Android role/permission round-trip. */
internal object AppointmentSimSelectionPolicy {
    fun index(available: List<Int>, previous: Int?, saved: Int?): Int? {
        if (available.isEmpty()) return null
        val intended = previous ?: saved
        val found = available.indexOf(intended)
        if (previous != null && found < 0) return null
        return if (found >= 0) found else 0
    }
}
