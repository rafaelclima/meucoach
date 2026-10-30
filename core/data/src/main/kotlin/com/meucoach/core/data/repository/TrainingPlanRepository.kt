package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.PlannedSessionEntity
import com.meucoach.core.data.db.entity.WeeklyPlanEntity
import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import java.time.Clock
import java.time.LocalDate

class TrainingPlanRepository(private val database: MeuCoachDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.trainingPlanDao()

    fun observeWeek(weekStart: LocalDate): Flow<WeeklyPlan?> = combine(
        dao.observePlan(weekStart.toString()),
        dao.observeSessions(weekStart.toString()),
    ) { plan, sessions -> plan?.toModel(sessions) }

    suspend fun getWeek(weekStart: LocalDate): WeeklyPlan? {
        val plan = dao.getPlan(weekStart.toString()) ?: return null
        return plan.toModel(dao.getSessions(weekStart.toString()))
    }

    suspend fun saveWeek(plan: WeeklyPlan) = database.withTransaction {
        val weekStart = plan.weekStart.toString()
        dao.upsertPlan(
            WeeklyPlanEntity(
                weekStart = weekStart,
                createdAt = clock.millis(),
                confidence = plan.confidence,
                rationale = plan.rationale,
                weeklyMinutes = plan.weeklyMinutes,
                longRunMinutes = plan.longRunMinutes,
                qualityCount = plan.qualityCount,
                ruleIdsJson = plan.ruleIds.toJson(),
                evidenceIdsJson = plan.evidenceIds.toJson(),
            ),
        )
        dao.deleteSessions(weekStart)
        dao.insertSessions(
            plan.sessions.map { session ->
                PlannedSessionEntity(
                    id = plannedSessionId(plan.weekStart, session.date),
                    weekStart = weekStart,
                    date = session.date.toString(),
                    type = session.type,
                    title = session.title,
                    durationMin = session.durationMin,
                    rationale = session.rationale,
                    ruleIdsJson = session.ruleIds.toJson(),
                    evidenceIdsJson = session.evidenceIds.toJson(),
                    status = session.status,
                    skipReason = session.skipReason,
                    completedAt = if (session.status == SessionStatus.DONE) clock.millis() else null,
                )
            },
        )
    }

    suspend fun updateStatus(
        weekStart: LocalDate,
        date: LocalDate,
        status: SessionStatus,
        skipReason: String? = null,
    ): Boolean = dao.updateStatus(
        id = plannedSessionId(weekStart, date),
        status = status.name,
        skipReason = skipReason,
        completedAt = if (status == SessionStatus.DONE) clock.millis() else null,
    ) > 0

    suspend fun deleteWeek(weekStart: LocalDate): Int = database.withTransaction {
        dao.deleteSessions(weekStart.toString())
        dao.deletePlan(weekStart.toString())
    }

    private fun WeeklyPlanEntity.toModel(sessions: List<PlannedSessionEntity>) = WeeklyPlan(
        weekStart = LocalDate.parse(weekStart),
        sessions = sessions.map { it.toModel() },
        weeklyMinutes = weeklyMinutes,
        longRunMinutes = longRunMinutes,
        qualityCount = qualityCount,
        rationale = rationale,
        confidence = confidence,
        ruleIds = ruleIdsJson.toList(),
        evidenceIds = evidenceIdsJson.toList(),
    )

    private fun PlannedSessionEntity.toModel() = PlannedSession(
        date = LocalDate.parse(date),
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = rationale,
        ruleIds = ruleIdsJson.toList(),
        evidenceIds = evidenceIdsJson.toList(),
        status = status,
        skipReason = skipReason,
    )

    private fun List<String>.toJson(): String = JSONArray(this).toString()

    private fun String.toList(): List<String> {
        val array = JSONArray(this)
        return List(array.length()) { index -> array.getString(index) }
    }
}
