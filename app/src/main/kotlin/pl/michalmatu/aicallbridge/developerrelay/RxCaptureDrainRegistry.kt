package pl.michalmatu.aicallbridge.developerrelay

/**
 * Serializes open RX-window finalization with the archive's END marker.
 * Finalizers run while this owner lock is held; subsequent callbacks cannot
 * enqueue audio after the archive was marked complete.
 */
internal class RxCaptureDrainRegistry {
    private val lock = Any()
    private val pending = linkedSetOf<Handle>()
    private var ended = false

    inner class Handle internal constructor(private val drain: () -> Unit, private var finished: Boolean) {
        fun finish(): Boolean = synchronized(lock) {
            if (finished) return@synchronized false
            finished = true
            pending.remove(this)
            try { drain() } catch (_: Throwable) { /* missing WAV is never a session failure */ }
            true
        }
    }

    fun open(drain: () -> Unit): Handle = synchronized(lock) {
        Handle(drain, ended).also { if (!ended) pending.add(it) }
    }

    fun finishAll() = synchronized(lock) {
        if (ended) return@synchronized
        ended = true
        pending.toList().forEach { it.finish() }
    }
}
