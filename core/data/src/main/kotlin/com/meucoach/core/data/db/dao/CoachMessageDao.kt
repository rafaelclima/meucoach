package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.CoachMessageEntity

@Dao
interface CoachMessageDao {
    /** Returns `-1` when the same `(kind, inputsHash)` already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: CoachMessageEntity): Long

    @Query("SELECT * FROM coach_messages ORDER BY createdAt, id")
    suspend fun getAll(): List<CoachMessageEntity>

    @Query("SELECT * FROM coach_messages WHERE kind = :kind AND inputsHash = :inputsHash LIMIT 1")
    suspend fun find(kind: String, inputsHash: String): CoachMessageEntity?

    @Query("DELETE FROM coach_messages WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM coach_messages")
    suspend fun deleteAll(): Int
}
