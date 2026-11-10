package pl.michalmatu.aicallbridge.incoming

/** The observed end of a Call alone does not prove that the AI end_call caused it. */
internal enum class IncomingEndToolOutcome { PENDING, CONFIRMED, UNKNOWN }

internal object IncomingEndToolEvidencePolicy {
    fun decide(
        callDisconnected: Boolean,
        endingOwnerAtDisconnect: Boolean,
    ): IncomingEndToolOutcome = when {
        !callDisconnected -> if (endingOwnerAtDisconnect) IncomingEndToolOutcome.PENDING
                             else IncomingEndToolOutcome.UNKNOWN
        endingOwnerAtDisconnect -> IncomingEndToolOutcome.CONFIRMED
        else -> IncomingEndToolOutcome.UNKNOWN
    }
}

/**
 * A Telecom disconnect callback can arrive before Run.finish drains the last
 * RX window. The first terminal outcome is immutable; END/COMPLETE may follow
 * only after runtime completion or immediately if no runtime started.
 */
internal class IncomingEndToolArchiveGate {
    private var started = false
    private var settled = false
    private var terminal: IncomingEndToolOutcome? = null

    @Synchronized fun onRuntimeStart() { started = true }
    @Synchronized fun runtimePending(): Boolean = started && !settled

    @Synchronized fun onTelecomEnd(outcome: IncomingEndToolOutcome): IncomingEndToolOutcome? {
        require(outcome != IncomingEndToolOutcome.PENDING)
        if (terminal == null) terminal = outcome
        return if (!started || settled) terminal else null
    }

    @Synchronized fun onRuntimeFinished(): IncomingEndToolOutcome? {
        settled = true
        return terminal
    }
}
