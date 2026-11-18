package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Compact memory of one closed week (Room v19, ADR 0052).
 *
 * `weekStart` is the natural key: at most one summary per reviewed week. The
 * narrative is the validated AI rewrite of the motor facts; `inputsHash` ties
 * it back to the review payload and never replaces the engine as authority.
 */
@Entity(tableName = "coach_summaries")
data class CoachSummaryEntity(
    @PrimaryKey val weekStart: String,
    val inputsHash: String,
    val title: String,
    val body: String,
    /** JSON array of the validated bullets. */
    val bulletsJson: String,
    val createdAt: Long,
)
