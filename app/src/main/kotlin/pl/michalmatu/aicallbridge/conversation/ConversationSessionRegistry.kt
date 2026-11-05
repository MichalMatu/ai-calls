package pl.michalmatu.aicallbridge.conversation

/** Owns one active media session. A rejected start never constructs or mutates another session. */
internal class ConversationSessionRegistry<T : Any> {
    private var current: T? = null
    @Synchronized fun tryCreate(factory: () -> T): T? {
        if (current != null) return null
        return factory().also { current = it }
    }
    @Synchronized fun release(session: T) {
        if (current === session) current = null
    }
}
