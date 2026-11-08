package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meucoach.core.data.db.entity.WeeklyPlanRevisionEntity

@Dao
interface WeeklyPlanRevisionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(revision: WeeklyPlanRevisionEntity)

    @Query("SELECT * FROM weekly_plan_revisions WHERE weekStart = :weekStart ORDER BY toVersion")
    suspend fun getForWeek(weekStart: String): List<WeeklyPlanRevisionEntity>

    @Query("SELECT * FROM weekly_plan_revisions WHERE weekStart = :weekStart AND toVersion = :toVersion")
    suspend fun get(weekStart: String, toVersion: Int): WeeklyPlanRevisionEntity?

    @Query("SELECT * FROM weekly_plan_revisions ORDER BY weekStart, toVersion")
    suspend fun getAll(): List<WeeklyPlanRevisionEntity>

    @Query("DELETE FROM weekly_plan_revisions")
    suspend fun deleteAll(): Int
}
