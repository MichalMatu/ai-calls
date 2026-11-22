package pl.michalmatu.aicallbridge.domain.models

import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode

/** Unformatted Home data. The UI chooses localized strings, never reads storage. */
internal data class HomeIncomingReadiness(
    val mode: AnsweringMode,
    val selectedSimPresent: Boolean,
    val hostReady: Boolean,
)

internal data class HomeOverviewSnapshot(
    val taskCount: Result<Int>,
    val notebookCount: Result<Int>,
    val incoming: Result<HomeIncomingReadiness>,
)
