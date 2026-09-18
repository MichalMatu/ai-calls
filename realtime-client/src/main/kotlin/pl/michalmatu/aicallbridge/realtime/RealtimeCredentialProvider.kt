package pl.michalmatu.aicallbridge.realtime

/** Fetches a backend-issued short-lived Realtime client secret for one client session. */
interface RealtimeCredentialProvider {
    suspend fun fetchClientSecret(): Result<RealtimeClientSecret>
}
