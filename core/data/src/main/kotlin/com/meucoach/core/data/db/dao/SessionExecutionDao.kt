package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.meucoach.core.data.db.entity.SessionExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionExecutionDao {
    @Insert
    suspend fun insert(execution: SessionExecutionEntity)

    @Update
    suspend fun update(execution: SessionExecutionEntity): Int

    @Query("SELECT * FROM session_executions WHERE id = :id")
    suspend fun findById(id: String): SessionExecutionEntity?

    @Query("SELECT * FROM session_executions WHERE plannedSessionId = :plannedSessionId LIMIT 1")
    suspend fun findBySession(plannedSessionId: String): SessionExecutionEntity?

    @Query("SELECT * FROM session_executions WHERE activityId = :activityId LIMIT 1")
    suspend fun findByActivity(activityId: String): SessionExecutionEntity?

    @Query("SELECT * FROM session_executions ORDER BY createdAt")
    suspend fun getAll(): List<SessionExecutionEntity>

    @Query("SELECT activityId FROM session_executions WHERE activityId IS NOT NULL")
    fun observeLinkedActivityIds(): Flow<List<String>>

    @Query("DELETE FROM session_executions WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String): Int

    @Query(
        "DELETE FROM session_executions WHERE plannedSessionId IN " +
            "(SELECT id FROM planned_sessions WHERE weekStart = :weekStart)",
    )
    suspend fun deleteForWeek(weekStart: String): Int

    @Query("DELETE FROM session_executions")
    suspend fun deleteAll(): Int
}
