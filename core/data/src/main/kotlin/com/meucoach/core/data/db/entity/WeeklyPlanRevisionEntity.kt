package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.model.DataConfidence

/**
 * Append-only audit trail of accepted replanning revisions (ADR 0028).
 *
 * The row keeps the metadata of the version it replaced and a JSON snapshot of
 * the strictly future `PLANNED` sessions that were removed, so a revision can
 * be explained without rewriting the original version.
 */
@Entity(
    tableName = "weekly_plan_revisions",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["weekStart"],
            childColumns = ["weekStart"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("weekStart")],
)
data class WeeklyPlanRevisionEntity(
    @PrimaryKey val id: String,
    val weekStart: String,
    val fromVersion: Int,
    val toVersion: Int,
    val fromInputsHash: String?,
    val toInputsHash: String,
    val fromAvailabilityHash: String?,
    val toAvailabilityHash: String,
    val generatorVersion: String,
    /** JSON metadata of the version that was replaced. */
    val previousPlanJson: String,
    /** JSON array with the future `PLANNED` sessions removed by the revision. */
    val replacedSessionsJson: String,
    val trigger: String,
    val reason: String,
    val ruleIdsJson: String,
    val evidenceIdsJson: String,
    val confidence: DataConfidence,
    val createdAt: Long,
)
