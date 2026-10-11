package com.figapps.fractioncalculatorv1.wire

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WireDatasheetDao {

    @Insert
    suspend fun insert(entity: WireDatasheet): Long

    @Update
    suspend fun update(entity: WireDatasheet)

    @Delete
    suspend fun delete(entity: WireDatasheet)

    @Query("SELECT * FROM wire_datasheets WHERE id = :id")
    suspend fun findById(id: Long): WireDatasheet?

    /** Used to reject a re-import of a PDF that is already in the library. */
    @Query("SELECT * FROM wire_datasheets WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(sha256: String): WireDatasheet?

    @Query("SELECT * FROM wire_datasheets")
    fun observeAll(): Flow<List<WireDatasheet>>

    @Query("SELECT COUNT(*) FROM wire_datasheets")
    suspend fun count(): Int

    @Query("SELECT fileName FROM wire_datasheets")
    suspend fun referencedFileNames(): List<String>
}
