package pl.michalmatu.aicallbridge.session

import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall
import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionFollowup

/** One-shot result channel bound to the Realtime generation that produced a function call. */
fun interface CallRealtimeFunctionResponder {
    fun submit(outputJson: String): Result<Unit>

    /**
     * Submit a function result with an explicit response-scoped follow-up policy.
     *
     * Existing responders remain source compatible for ordinary automatic follow-ups. A forced or
     * no-tools follow-up must be implemented explicitly so safety-critical routing cannot silently
     * degrade to `auto`.
     */
    fun submit(
        outputJson: String,
        followup: RealtimeFunctionFollowup,
    ): Result<Unit> = when (followup) {
        RealtimeFunctionFollowup.Auto -> submit(outputJson)
        else -> Result.failure(
            UnsupportedOperationException("Realtime function responder does not support explicit follow-up"),
        )
    }
}

/** App-layer hook for deterministic business/policy handling of one typed Realtime function call. */
fun interface CallRealtimeFunctionCallHandler {
    fun onFunctionCall(call: RealtimeFunctionCall, responder: CallRealtimeFunctionResponder)
}
