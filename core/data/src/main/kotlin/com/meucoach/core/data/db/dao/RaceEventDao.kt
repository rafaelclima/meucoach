package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.meucoach.core.data.db.entity.RaceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RaceEventDao {
    @Insert
    suspend fun insert(event: RaceEventEntity)

    @Update
    suspend fun update(event: RaceEventEntity): Int

    @Query("SELECT * FROM race_events WHERE id = :id")
    suspend fun findById(id: String): RaceEventEntity?

    @Query("SELECT * FROM race_events WHERE activeKey = 'ACTIVE' LIMIT 1")
    suspend fun findPlanned(): RaceEventEntity?

    @Query("SELECT * FROM race_events WHERE activityId = :activityId LIMIT 1")
    suspend fun findByActivity(activityId: String): RaceEventEntity?

    @Query(
        "SELECT * FROM race_events WHERE targetDate = :date " +
            "ORDER BY CASE status WHEN 'PLANNED' THEN 0 WHEN 'COMPLETED' THEN 1 ELSE 2 END, updatedAt DESC",
    )
    suspend fun findForDate(date: String): List<RaceEventEntity>

    @Query("SELECT targetDate FROM race_events WHERE targetDate BETWEEN :start AND :end")
    suspend fun datesBetween(start: String, end: String): List<String>

    @Query("SELECT * FROM race_events ORDER BY targetDate, createdAt")
    suspend fun getAll(): List<RaceEventEntity>

    @Query("SELECT * FROM race_events ORDER BY targetDate, createdAt")
    fun observeAll(): Flow<List<RaceEventEntity>>

    @Query("DELETE FROM race_events")
    suspend fun deleteAll(): Int
}
