package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.meucoach.core.data.db.entity.DailyCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCheckInDao {
    @Query("SELECT * FROM daily_check_ins WHERE date = :date")
    suspend fun get(date: String): DailyCheckInEntity?

    @Query("SELECT * FROM daily_check_ins WHERE date = :date")
    fun observe(date: String): Flow<DailyCheckInEntity?>

    @Query("SELECT * FROM daily_check_ins ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyCheckInEntity>>

    @Upsert
    suspend fun upsert(checkIn: DailyCheckInEntity)

    @Query("DELETE FROM daily_check_ins WHERE date = :date")
    suspend fun delete(date: String): Int

    @Query("DELETE FROM daily_check_ins")
    suspend fun deleteAll(): Int
}
