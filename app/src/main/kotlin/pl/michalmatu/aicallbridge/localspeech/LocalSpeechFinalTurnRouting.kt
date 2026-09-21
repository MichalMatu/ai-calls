package pl.michalmatu.aicallbridge.localspeech

import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

/** Product-owned optional selector used only after STT has produced one final transcript. */
internal fun interface LocalSpeechFinalTurnRouteSelector {
    fun select(finalTranscript: String): TextCallFinalTurnRoute
}

internal fun selectLocalSpeechFinalTurnRoute(
    finalTranscript: String,
    selector: LocalSpeechFinalTurnRouteSelector?,
): TextCallFinalTurnRoute {
    require(finalTranscript.isNotBlank()) { "final_transcript_must_not_be_blank" }
    return selector?.select(finalTranscript) ?: TextCallFinalTurnRoute.Generate
}
