package com.meucoach.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import java.time.LocalDate

@Entity(tableName = "weekly_plans")
data class WeeklyPlanEntity(
    @PrimaryKey val weekStart: String,
    val createdAt: Long,
    val createdOn: String,
    val confidence: DataConfidence,
    val rationale: String,
    val weeklyMinutes: Int,
    val longRunMinutes: Int?,
    val qualityCount: Int,
    val ruleIdsJson: String,
    val evidenceIdsJson: String,
    /** Monotonic plan version; every accepted revision increments it (ADR 0028). */
    @ColumnInfo(defaultValue = "1") val version: Int = 1,
    /** Reason of the last accepted revision; null for the original version. */
    val revisionReason: String? = null,
    /** Epoch millis of the last accepted revision; null for the original version. */
    val revisedAt: Long? = null,
    /** Hash of the inputs that produced this version; null when unknown (legacy v12). */
    val inputsHash: String? = null,
    /** Availability baseline of this version; null when unknown (legacy v12). */
    val availabilityHash: String? = null,
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
    indices = [
        Index("weekStart"),
        Index("date"),
        // ADR 0024: one activity can be linked to at most one session.
        Index(value = ["activityId"], unique = true),
    ],
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
    val targetsJson: String,
    val status: SessionStatus,
    val skipReason: String?,
    val completedAt: Long?,
    val completionEvidence: CompletionEvidence? = null,
    val executionId: String? = null,
    val activityId: String? = null,
    val closureReason: SessionClosureReason? = null,
    val closedAt: Long? = null,
)

fun plannedSessionId(weekStart: LocalDate, date: LocalDate): String = "$weekStart-$date"
