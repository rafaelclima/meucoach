package com.meucoach.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.meucoach.core.data.db.entity.PlannedSessionEntity
import com.meucoach.core.data.db.entity.WeeklyPlanEntity
import com.meucoach.core.domain.prescription.WorkoutType
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM weekly_plans WHERE weekStart = :weekStart")
    suspend fun getPlan(weekStart: String): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plans WHERE weekStart = :weekStart")
    fun observePlan(weekStart: String): Flow<WeeklyPlanEntity?>

    @Query("SELECT * FROM planned_sessions WHERE weekStart = :weekStart ORDER BY date")
    suspend fun getSessions(weekStart: String): List<PlannedSessionEntity>

    @Query("SELECT * FROM planned_sessions WHERE weekStart = :weekStart ORDER BY date")
    fun observeSessions(weekStart: String): Flow<List<PlannedSessionEntity>>

    @Query("SELECT weekStart FROM weekly_plans WHERE weekStart < :weekStart ORDER BY weekStart DESC")
    suspend fun getPlanWeeksBefore(weekStart: String): List<String>

    @Upsert
    suspend fun upsertPlan(plan: WeeklyPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PlannedSessionEntity>)

    @Query(
        "UPDATE planned_sessions SET type = :type, title = :title, durationMin = :durationMin, " +
            "rationale = :rationale, ruleIdsJson = :ruleIdsJson, evidenceIdsJson = :evidenceIdsJson " +
            "WHERE id = :id",
    )
    suspend fun updateSessionContent(
        id: String,
        type: WorkoutType,
        title: String,
        durationMin: Int?,
        rationale: String,
        ruleIdsJson: String,
        evidenceIdsJson: String,
    ): Int

    @Query("DELETE FROM planned_sessions WHERE weekStart = :weekStart")
    suspend fun deleteSessions(weekStart: String): Int

    @Query("DELETE FROM weekly_plans WHERE weekStart = :weekStart")
    suspend fun deletePlan(weekStart: String): Int

    @Query(
        "UPDATE planned_sessions SET status = :status, skipReason = :skipReason, " +
            "completedAt = :completedAt WHERE id = :id",
    )
    suspend fun updateStatus(id: String, status: String, skipReason: String?, completedAt: Long?): Int
}
