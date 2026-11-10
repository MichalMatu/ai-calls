package pl.michalmatu.aicallbridge.campaign

import java.util.concurrent.atomic.AtomicReference

/** One exact owned session. STOP and completion may race; cleanup happens at most once. */
internal class CampaignOwnedCallLifecycle<T : Any>(
    private val cleanup: (T) -> String,
) {
    private val active = AtomicReference<T?>(null)

    fun bind(session: T): Boolean = active.compareAndSet(null, session)

    /** Read-only exact-owner guard for model-requested end_call. */
    fun isCurrent(session: T): Boolean = active.get() === session

    /** Transfers ownership to the human without running disconnect cleanup. */
    fun detachIf(matches: (T) -> Boolean): T? {
        val current = active.get() ?: return null
        return if (matches(current) && active.compareAndSet(current, null)) current else null
    }

    fun release(session: T): String =
        if (active.compareAndSet(session, null)) cleanup(session) else "already_released"

    fun releaseCurrent(): String =
        active.getAndSet(null)?.let(cleanup) ?: "no_owned_call"
}
