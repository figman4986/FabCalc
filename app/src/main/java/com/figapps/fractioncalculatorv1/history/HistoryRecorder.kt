package com.figapps.fractioncalculatorv1.history

/** A completed calculation worth remembering, independent of how/where it is stored. */
data class HistoryDraft(
    val toolId: String,
    val toolName: String,
    val inputs: String,
    val result: String,
    val detail: String,
)

/**
 * Records completed calculations. Tool screens receive one of these instead of reaching for a
 * global, so they stay decoupled and can be previewed/tested without a database.
 */
fun interface HistoryRecorder {
    suspend fun record(draft: HistoryDraft)
}

/** Drop-in recorder for previews and tests. */
val NoOpHistoryRecorder = HistoryRecorder { }
