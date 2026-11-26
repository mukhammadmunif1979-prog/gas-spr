package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GasEntryDao {
    @Query("SELECT * FROM gas_entries ORDER BY dateIso DESC, modelName ASC, binNumber ASC, shiftName ASC")
    fun observeAllEntries(): Flow<List<GasEntryEntity>>

    @Query("SELECT * FROM gas_entries ORDER BY dateIso ASC, modelName ASC, binNumber ASC, shiftName ASC")
    suspend fun getAllEntriesSnapshot(): List<GasEntryEntity>

    @Query(
        "SELECT * FROM gas_entries " +
            "WHERE dateIso = :dateIso AND modelName = :modelName " +
            "AND binNumber = :binNumber AND shiftName = :shiftName LIMIT 1",
    )
    suspend fun findExistingSlot(
        dateIso: String,
        modelName: String,
        binNumber: Int,
        shiftName: String,
    ): GasEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: GasEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<GasEntryEntity>)

    @Update
    suspend fun updateEntry(entry: GasEntryEntity)

    @Query("DELETE FROM gas_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM gas_entries")
    suspend fun deleteAll()
}
