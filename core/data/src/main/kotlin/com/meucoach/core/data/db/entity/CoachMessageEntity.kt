package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One validated turn the Coach produced (Room v19, ADR 0052).
 *
 * Only text that already passed `CoachResponseValidator` is persisted, so the
 * row is copy context, never a source of numbers, pace, heart rate or load.
 * `(kind, inputsHash)` is unique: the same motor context never duplicates the
 * conversation when the tab is reopened or retried.
 */
@Entity(
    tableName = "coach_messages",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["kind", "inputsHash"], unique = true),
    ],
)
data class CoachMessageEntity(
    @PrimaryKey val id: String,
    /** `EXPLAIN` or `REVIEW`; the MVP has no free-text user turn. */
    val kind: String,
    val inputsHash: String,
    val title: String,
    val body: String,
    /** JSON array of the validated bullets. */
    val bulletsJson: String,
    val createdAt: Long,
)
