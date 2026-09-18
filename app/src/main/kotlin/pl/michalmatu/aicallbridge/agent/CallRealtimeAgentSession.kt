package pl.michalmatu.aicallbridge.agent

import pl.michalmatu.aicallbridge.session.CallRealtimeSessionOrchestratorSnapshot

/** Minimal app-owned surface for one Telephone Agent Realtime session generation. */
interface CallRealtimeAgentSession : AutoCloseable {
    fun start(): Long
    fun snapshot(): CallRealtimeSessionOrchestratorSnapshot
    fun takeOverNow()
    fun hasPendingUserDecision(): Boolean
    fun approvePendingProposal(): Result<CallProposal>
    fun rejectPendingProposal(): Result<CallProposal>
}
