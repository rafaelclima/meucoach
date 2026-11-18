package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.CoachMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachMessageDao {
    /** Returns `-1` when the same `(kind, inputsHash)` already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: CoachMessageEntity): Long

    @Query("SELECT * FROM coach_messages ORDER BY createdAt, id")
    suspend fun getAll(): List<CoachMessageEntity>

    /** Read-only history: newest turn first. */
    @Query("SELECT * FROM coach_messages ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<CoachMessageEntity>>

    @Query("SELECT * FROM coach_messages WHERE kind = :kind AND inputsHash = :inputsHash LIMIT 1")
    suspend fun find(kind: String, inputsHash: String): CoachMessageEntity?

    @Query("DELETE FROM coach_messages WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM coach_messages WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM coach_messages")
    suspend fun deleteAll(): Int
}
