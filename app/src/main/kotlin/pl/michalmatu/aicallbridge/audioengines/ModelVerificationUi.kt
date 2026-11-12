package pl.michalmatu.aicallbridge.audioengines

/** Display state is separate from native readiness; downloading never implies compatibility. */
internal enum class ModelVerificationStatus {
    VERIFIED_TEXT, VERIFIED_AUDIO, REQUIRED_TEXT, REQUIRED_AUDIO, DOWNLOADED_UNVERIFIED
}

internal object ModelVerificationUi {
    fun status(verified: Boolean, requiresText: Boolean, audio: Boolean): ModelVerificationStatus =
        when {
            verified && audio -> ModelVerificationStatus.VERIFIED_AUDIO
            verified -> ModelVerificationStatus.VERIFIED_TEXT
            audio -> ModelVerificationStatus.REQUIRED_AUDIO
            requiresText -> ModelVerificationStatus.REQUIRED_TEXT
            else -> ModelVerificationStatus.DOWNLOADED_UNVERIFIED
        }

    fun checkVisible(installed: Boolean, downloading: Boolean): Boolean =
        installed && !downloading
}
