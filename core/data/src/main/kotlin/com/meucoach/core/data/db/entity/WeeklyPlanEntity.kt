package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import java.time.LocalDate

@Entity(tableName = "weekly_plans")
data class WeeklyPlanEntity(
    @PrimaryKey val weekStart: String,
    val createdAt: Long,
    val confidence: DataConfidence,
    val rationale: String,
    val weeklyMinutes: Int,
    val longRunMinutes: Int?,
    val qualityCount: Int,
    val ruleIdsJson: String,
    val evidenceIdsJson: String,
)

@Entity(
    tableName = "planned_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["weekStart"],
            childColumns = ["weekStart"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("weekStart"), Index("date")],
)
data class PlannedSessionEntity(
    @PrimaryKey val id: String,
    val weekStart: String,
    val date: String,
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val rationale: String,
    val ruleIdsJson: String,
    val evidenceIdsJson: String,
    val status: SessionStatus,
    val skipReason: String?,
    val completedAt: Long?,
)

fun plannedSessionId(weekStart: LocalDate, date: LocalDate): String = "$weekStart-$date"
