package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence

/**
 * One version of the daily decision. Editing the check-in appends a new row and
 * points [supersedesId] to the previous one; nothing is overwritten.
 */
@Entity(
    tableName = "daily_prescriptions",
    indices = [Index("date")],
)
data class DailyPrescriptionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val plannedSessionId: String?,
    val plannedType: WorkoutType?,
    val plannedTitle: String?,
    val plannedDurationMin: Int?,
    val prescribedType: WorkoutType,
    val title: String,
    val prescribedDurationMin: Int?,
    val targetsJson: String,
    val adjustment: PrescriptionAdjustment,
    val rationale: String,
    val ruleIdsJson: String,
    val evidenceIdsJson: String,
    val confidence: DataConfidence,
    val engineVersion: String,
    val inputsHash: String,
    val supersedesId: String?,
    val createdAt: Long,
)
