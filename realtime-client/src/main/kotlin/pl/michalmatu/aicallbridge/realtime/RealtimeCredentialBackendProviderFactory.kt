package pl.michalmatu.aicallbridge.realtime

/**
 * Transport-neutral public composition boundary for the Android -> developer-backend credential
 * path. Networking-library request types stay private to the realtime-client module.
 */
object RealtimeCredentialBackendProviderFactory {
    @JvmStatic
    fun create(
        endpoint: String,
        bearerTokenProvider: () -> String,
    ): RealtimeCredentialProvider {
        val requestFactory = RealtimeCredentialBackendRequestFactory(
            endpoint = endpoint,
            bearerTokenProvider = bearerTokenProvider,
        )
        return BackendRealtimeCredentialProvider(requestFactory::create)
    }
}
