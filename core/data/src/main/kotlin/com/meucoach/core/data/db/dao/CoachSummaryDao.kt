package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.CoachSummaryEntity

@Dao
interface CoachSummaryDao {
    /** One row per week: a newer validated narrative replaces the previous one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: CoachSummaryEntity)

    @Query("SELECT * FROM coach_summaries ORDER BY weekStart DESC")
    suspend fun getAll(): List<CoachSummaryEntity>

    @Query("SELECT * FROM coach_summaries WHERE weekStart = :weekStart")
    suspend fun find(weekStart: String): CoachSummaryEntity?

    @Query("DELETE FROM coach_summaries WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM coach_summaries")
    suspend fun deleteAll(): Int
}
