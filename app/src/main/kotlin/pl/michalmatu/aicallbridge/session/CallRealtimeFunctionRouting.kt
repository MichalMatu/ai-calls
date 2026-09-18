package pl.michalmatu.aicallbridge.session

import pl.michalmatu.aicallbridge.realtime.RealtimeFunctionCall

/** One-shot result channel bound to the Realtime generation that produced a function call. */
fun interface CallRealtimeFunctionResponder {
    fun submit(outputJson: String): Result<Unit>
}

/** App-layer hook for deterministic business/policy handling of one typed Realtime function call. */
fun interface CallRealtimeFunctionCallHandler {
    fun onFunctionCall(call: RealtimeFunctionCall, responder: CallRealtimeFunctionResponder)
}
