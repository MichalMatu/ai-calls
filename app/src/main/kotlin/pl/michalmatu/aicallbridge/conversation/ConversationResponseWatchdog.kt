package pl.michalmatu.aicallbridge.conversation

/** One outstanding response timeout. Superseded, completed and stopped turns cannot fire it.
 * The owner validates exact Call/media generation again before using its callback.
 */
internal class ConversationResponseWatchdog(
    private val timeoutMs: Long,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
) {
    init { require(timeoutMs > 0) }

    private val lock = Any()
    private var pending: Runnable? = null

    fun arm(onTimeout: () -> Unit) {
        val task = object : Runnable {
            override fun run() {
                val claimed = synchronized(lock) {
                    if (pending !== this) false else {
                        pending = null
                        true
                    }
                }
                if (claimed) onTimeout()
            }
        }
        val previous = synchronized(lock) {
            pending.also { pending = task }
        }
        previous?.let(removeCallbacks)
        postDelayed(task, timeoutMs)
    }

    fun cancel() {
        val previous = synchronized(lock) {
            pending.also { pending = null }
        }
        previous?.let(removeCallbacks)
    }
}
