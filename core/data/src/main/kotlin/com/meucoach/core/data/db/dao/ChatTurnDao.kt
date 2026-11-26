package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.ChatTurnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatTurnDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(turn: ChatTurnEntity): Long

    @Query("SELECT * FROM chat_turns ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ChatTurnEntity>>

    @Query("SELECT * FROM chat_turns ORDER BY createdAt DESC")
    suspend fun getAll(): List<ChatTurnEntity>

    @Query("DELETE FROM chat_turns WHERE expiresAt < :now")
    suspend fun deleteOlderThan(now: Long): Int

    @Query("DELETE FROM chat_turns WHERE origin = :origin AND inputsHash = :inputsHash")
    suspend fun deleteByOriginAndInputsHash(origin: String, inputsHash: String): Int

    @Query("DELETE FROM chat_turns WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM chat_turns")
    suspend fun deleteAll(): Int
}
