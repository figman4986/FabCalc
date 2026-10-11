package com.figapps.fractioncalculatorv1.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded calculation. History grows without bound by design; the user can delete individual
 * entries or clear everything.
 *
 * Indices speed up the newest-first listing and the per-tool grouping performed by the UI.
 */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["toolId"]),
    ]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val toolName: String,
    val inputs: String,
    val result: String,
    val detail: String,
    val timestamp: Long,
)
