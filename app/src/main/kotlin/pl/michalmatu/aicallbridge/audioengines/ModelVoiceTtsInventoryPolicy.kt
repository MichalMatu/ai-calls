package pl.michalmatu.aicallbridge.audioengines

/**
 * Pure selection policy for Android TTS inventory. The activity still owns engine lifecycle,
 * background worker, generation fencing and preview; no voice can be played from this policy.
 */
internal object ModelVoiceTtsInventoryPolicy {
    fun <T> availableOffline(
        installedEngines: Collection<String>,
        selectedEngine: String?,
        voices: Collection<T>,
        language: String,
        piperRequired: Boolean,
        languageOf: (T) -> String,
        networkRequired: (T) -> Boolean,
        piperCapable: (T) -> Boolean,
        nameOf: (T) -> String,
    ): List<T> {
        // An explicitly selected engine must never silently fall back to the system engine.
        require(selectedEngine == null || installedEngines.any { it == selectedEngine })
        return voices.filter {
            languageOf(it) == language && !networkRequired(it) &&
                (!piperRequired || piperCapable(it))
        }.sortedBy(nameOf)
    }

    fun displayEngineName(packageName: String, suppliedLabel: String): String = when (packageName) {
        "com.google.android.tts" -> "Google"
        "com.samsung.SMT" -> "Samsung"
        PiperServiceContract.PACKAGE -> "Piper"
        else -> suppliedLabel
    }
}
