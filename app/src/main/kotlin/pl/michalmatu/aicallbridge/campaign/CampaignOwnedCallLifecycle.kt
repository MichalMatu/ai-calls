package pl.michalmatu.aicallbridge.campaign

import java.util.concurrent.atomic.AtomicReference

/** One exact owned session. STOP and completion may race; cleanup happens at most once. */
internal class CampaignOwnedCallLifecycle<T : Any>(
    private val cleanup: (T) -> String,
) {
    private val active = AtomicReference<T?>(null)

    fun bind(session: T): Boolean = active.compareAndSet(null, session)

    fun release(session: T): String =
        if (active.compareAndSet(session, null)) cleanup(session) else "already_released"

    fun releaseCurrent(): String =
        active.getAndSet(null)?.let(cleanup) ?: "no_owned_call"
}
