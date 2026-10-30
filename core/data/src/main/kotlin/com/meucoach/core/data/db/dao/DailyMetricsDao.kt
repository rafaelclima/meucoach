package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.meucoach.core.data.db.entity.DailyMetricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMetricsDao {
    @Query("SELECT * FROM daily_metrics WHERE date = :date")
    suspend fun get(date: String): DailyMetricsEntity?

    @Query("SELECT * FROM daily_metrics ORDER BY date")
    fun observeAll(): Flow<List<DailyMetricsEntity>>

    @Query("SELECT * FROM daily_metrics ORDER BY date")
    suspend fun getAll(): List<DailyMetricsEntity>

    @Upsert
    suspend fun upsert(metrics: DailyMetricsEntity)

    @Query("DELETE FROM daily_metrics WHERE date = :date")
    suspend fun delete(date: String): Int

    @Query("DELETE FROM daily_metrics")
    suspend fun deleteAll(): Int
}
