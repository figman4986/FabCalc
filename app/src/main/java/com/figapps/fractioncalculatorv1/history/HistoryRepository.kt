package com.figapps.fractioncalculatorv1.history

import kotlinx.coroutines.flow.Flow

/**
 * Thin façade over [HistoryDao]. Keeps the rest of the app unaware of Room.
 * Growth is intentionally unbounded — history persists until the user clears it.
 */
class HistoryRepository(private val dao: HistoryDao) : HistoryRecorder {

    val entries: Flow<List<HistoryEntry>> = dao.observeAll()

    override suspend fun record(draft: HistoryDraft) {
        dao.insert(
            HistoryEntry(
                toolId = draft.toolId,
                toolName = draft.toolName,
                inputs = draft.inputs,
                result = draft.result,
                detail = draft.detail,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(entry: HistoryEntry) = dao.delete(entry)

    suspend fun clear() = dao.clear()
}
