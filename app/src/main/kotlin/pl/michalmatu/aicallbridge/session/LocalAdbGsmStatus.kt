package pl.michalmatu.aicallbridge.session

/**
 * Keep ADB authentication separate from GSM host readiness.
 * A saved key is not evidence that Android still trusts or connects it.
 */
internal object LocalAdbGsmStatus {
    enum class AdbState {
        CONNECTED, KEY_STORED_OFFLINE, DEBUGGING_DISABLED, NOT_CONFIGURED, UNAVAILABLE
    }

    data class Snapshot(val gsmReady: Boolean, val adbState: AdbState)

    fun resolve(keyStored: Boolean, wifiDebugEnabled: Int, authenticated: Boolean): AdbState =
        when {
            !keyStored -> AdbState.NOT_CONFIGURED
            wifiDebugEnabled == 0 -> AdbState.DEBUGGING_DISABLED
            authenticated -> AdbState.CONNECTED
            else -> AdbState.KEY_STORED_OFFLINE
        }
}
