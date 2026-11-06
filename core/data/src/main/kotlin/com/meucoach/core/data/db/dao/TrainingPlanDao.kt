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

    @Query("SELECT * FROM planned_sessions WHERE id = :id")
    suspend fun getSession(id: String): PlannedSessionEntity?

    @Query("SELECT * FROM planned_sessions WHERE weekStart = :weekStart ORDER BY date")
    suspend fun getSessions(weekStart: String): List<PlannedSessionEntity>

    @Query("SELECT * FROM planned_sessions WHERE activityId = :activityId LIMIT 1")
    suspend fun getSessionByActivityId(activityId: String): PlannedSessionEntity?

    @Query("SELECT activityId FROM planned_sessions WHERE activityId IS NOT NULL")
    fun observeLinkedActivityIds(): Flow<List<String>>

    @Query("SELECT * FROM planned_sessions WHERE weekStart = :weekStart ORDER BY date")
    fun observeSessions(weekStart: String): Flow<List<PlannedSessionEntity>>

    @Query("SELECT weekStart FROM weekly_plans WHERE weekStart < :weekStart ORDER BY weekStart DESC")
    suspend fun getPlanWeeksBefore(weekStart: String): List<String>

    @Query("SELECT * FROM weekly_plans ORDER BY weekStart")
    suspend fun getAllPlans(): List<WeeklyPlanEntity>

    @Query("SELECT * FROM planned_sessions ORDER BY weekStart, date")
    suspend fun getAllSessions(): List<PlannedSessionEntity>

    @Upsert
    suspend fun upsertPlan(plan: WeeklyPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PlannedSessionEntity>)

    @Query(
        "UPDATE planned_sessions SET type = :type, title = :title, durationMin = :durationMin, " +
            "rationale = :rationale, ruleIdsJson = :ruleIdsJson, evidenceIdsJson = :evidenceIdsJson, " +
            "targetsJson = :targetsJson WHERE id = :id",
    )
    suspend fun updateSessionContent(
        id: String,
        type: WorkoutType,
        title: String,
        durationMin: Int?,
        rationale: String,
        ruleIdsJson: String,
        evidenceIdsJson: String,
        targetsJson: String,
    ): Int

    @Query("DELETE FROM planned_sessions WHERE weekStart = :weekStart")
    suspend fun deleteSessions(weekStart: String): Int

    @Query("DELETE FROM planned_sessions")
    suspend fun deleteAllSessions(): Int

    @Query("DELETE FROM weekly_plans WHERE weekStart = :weekStart")
    suspend fun deletePlan(weekStart: String): Int

    @Query("DELETE FROM weekly_plans")
    suspend fun deleteAllPlans(): Int

    @Query(
        "UPDATE planned_sessions SET status = 'DONE', completionEvidence = :evidence, " +
            "executionId = :executionId, activityId = :activityId, completedAt = :completedAt, " +
            "closureReason = NULL, closedAt = NULL, skipReason = NULL " +
            "WHERE id = :id AND status != 'DONE'",
    )
    suspend fun markDone(
        id: String,
        evidence: String,
        executionId: String?,
        activityId: String?,
        completedAt: Long,
    ): Int

    @Query(
        "UPDATE planned_sessions SET status = 'CLOSED', closureReason = :reason, " +
            "skipReason = :note, closedAt = :closedAt, completedAt = NULL, " +
            "completionEvidence = NULL, executionId = NULL, activityId = NULL " +
            "WHERE id = :id AND status = 'PLANNED'",
    )
    suspend fun close(id: String, reason: String, note: String?, closedAt: Long): Int

    /** ADR 0025: a trashed activity cannot keep proving a session. */
    @Query(
        "UPDATE planned_sessions SET completionEvidence = 'LEGACY_UNVERIFIED' " +
            "WHERE activityId = :activityId AND status = 'DONE' AND completionEvidence = 'ACTIVITY'",
    )
    suspend fun suspendActivityEvidence(activityId: String): Int

    @Query(
        "UPDATE planned_sessions SET completionEvidence = 'ACTIVITY' " +
            "WHERE activityId = :activityId AND status = 'DONE' " +
            "AND completionEvidence = 'LEGACY_UNVERIFIED'",
    )
    suspend fun restoreActivityEvidence(activityId: String): Int

    /** Purge removes the link but keeps an honest, unverified historical record. */
    @Query(
        "UPDATE planned_sessions SET activityId = NULL, executionId = NULL, " +
            "completionEvidence = 'LEGACY_UNVERIFIED' " +
            "WHERE activityId = :activityId AND status = 'DONE'",
    )
    suspend fun purgeActivityLink(activityId: String): Int
}
